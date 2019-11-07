package de.metas.ui.web.view;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.adempiere.exceptions.AdempiereException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;

import de.metas.logging.LogManager;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilter.Builder;
import de.metas.ui.web.document.filter.DocumentFilterDescriptor;
import de.metas.ui.web.document.filter.DocumentFilterParam;
import de.metas.ui.web.document.filter.DocumentFilterParam.Operator;
import de.metas.ui.web.document.filter.DocumentFilterParamDescriptor;
import de.metas.ui.web.document.filter.DocumentFiltersList;
import de.metas.ui.web.document.filter.sql.SqlDocumentFilterConverterDecorator;
import de.metas.ui.web.document.geo_location.GeoLocationDocumentService;
import de.metas.ui.web.view.descriptor.SqlViewBinding;
import de.metas.ui.web.view.descriptor.SqlViewBindingFactory;
import de.metas.ui.web.view.descriptor.SqlViewCustomizerMap;
import de.metas.ui.web.view.descriptor.ViewLayout;
import de.metas.ui.web.view.descriptor.ViewLayoutFactory;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.descriptor.DocumentEntityDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.factory.DocumentDescriptorFactory;
import de.metas.ui.web.window.model.DocumentReference;
import de.metas.ui.web.window.model.DocumentReferencesService;
import de.metas.util.time.SystemTime;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/**
 * View factory which is based on {@link DocumentEntityDescriptor} having SQL repository.<br>
 * Creates {@link DefaultView}s with are backed by a {@link SqlViewBinding}.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
@Service
public class SqlViewFactory implements IViewFactory
{
	private static final Logger logger = LogManager.getLogger(SqlViewFactory.class);
	private final DocumentReferencesService documentReferencesService;
	private final ViewLayoutFactory viewLayouts;
	private final CompositeDefaultViewProfileIdProvider defaultProfileIdProvider;

	public SqlViewFactory(
			@NonNull final DocumentDescriptorFactory documentDescriptorFactory,
			@NonNull final DocumentReferencesService documentReferencesService,
			@NonNull final List<SqlViewCustomizer> viewCustomizersList,
			@NonNull final List<DefaultViewProfileIdProvider> defaultViewProfileIdProviders,
			@NonNull final List<SqlDocumentFilterConverterDecorator> converterDecorators,
			@NonNull final List<IViewInvalidationAdvisor> viewInvalidationAdvisors,
			@NonNull final GeoLocationDocumentService geoLocationDocumentService)
	{
		this.documentReferencesService = documentReferencesService;

		final SqlViewCustomizerMap viewCustomizers = SqlViewCustomizerMap.ofCollection(viewCustomizersList);
		logger.info("View customizers: {}", viewCustomizers);

		this.defaultProfileIdProvider = makeDefaultProfileIdProvider(defaultViewProfileIdProviders, viewCustomizers);
		logger.info("Default ProfileId providers: {}", this.defaultProfileIdProvider);

		final SqlViewBindingFactory viewBindingsFactory = SqlViewBindingFactory.builder()
				.documentDescriptorFactory(documentDescriptorFactory)
				.viewCustomizers(viewCustomizers)
				.converterDecorators(converterDecorators)
				.viewInvalidationAdvisors(viewInvalidationAdvisors)
				.build();

		this.viewLayouts = ViewLayoutFactory.builder()
				.documentDescriptorFactory(documentDescriptorFactory)
				.viewBindingsFactory(viewBindingsFactory)
				.viewCustomizers(viewCustomizers)
				.geoLocationDocumentService(geoLocationDocumentService)
				.build();
	}

	private static CompositeDefaultViewProfileIdProvider makeDefaultProfileIdProvider(
			final List<DefaultViewProfileIdProvider> providers,
			final SqlViewCustomizerMap viewCustomizersToExtractFallbacks)
	{
		final CompositeDefaultViewProfileIdProvider result = CompositeDefaultViewProfileIdProvider.of(providers);
		viewCustomizersToExtractFallbacks.forEachWindowIdAndProfileId(result::setDefaultProfileIdFallbackIfAbsent);
		return result;
	}

	@Override
	public List<ViewProfile> getAvailableProfiles(final WindowId windowId)
	{
		return viewLayouts.getAvailableProfiles(windowId);
	}

	public void setDefaultProfileId(@NonNull final WindowId windowId, final ViewProfileId profileId)
	{
		defaultProfileIdProvider.setDefaultProfileIdOverride(windowId, profileId);
	}

	@Override
	public ViewLayout getViewLayout(
			@NonNull final WindowId windowId,
			@NonNull final JSONViewDataType viewDataType,
			@Nullable final ViewProfileId profileId)
	{
		final ViewProfileId profileIdEffective = !ViewProfileId.isNull(profileId) ? profileId : defaultProfileIdProvider.getDefaultProfileIdByWindowId(windowId);
		return viewLayouts.getViewLayout(windowId, viewDataType, profileIdEffective);
	}

	@Override
	public DefaultView createView(final CreateViewRequest request)
	{
		final WindowId windowId = request.getViewId().getWindowId();

		final JSONViewDataType viewType = request.getViewType();
		final ViewProfileId profileId = !ViewProfileId.isNull(request.getProfileId()) ? request.getProfileId() : defaultProfileIdProvider.getDefaultProfileIdByWindowId(windowId);
		final SqlViewBinding sqlViewBinding = viewLayouts.getViewBinding(windowId, viewType.getRequiredFieldCharacteristic(), profileId);
		final IViewDataRepository viewDataRepository = new SqlViewDataRepository(sqlViewBinding);

		final DefaultView.Builder viewBuilder = DefaultView.builder(viewDataRepository)
				.setViewId(request.getViewId())
				.setViewType(viewType)
				.setProfileId(profileId)
				.setReferencingDocumentPaths(request.getReferencingDocumentPaths())
				.setParentViewId(request.getParentViewId())
				.setParentRowId(request.getParentRowId())
				.addStickyFilters(request.getStickyFilters())
				.addStickyFilter(extractReferencedDocumentFilter(windowId, request.getSingleReferencingDocumentPathOrNull()))
				.applySecurityRestrictions(request.isApplySecurityRestrictions())
				.viewInvalidationAdvisor(sqlViewBinding.getViewInvalidationAdvisor())
				.refreshViewOnChangeEvents(sqlViewBinding.isRefreshViewOnChangeEvents());

		final DocumentFiltersList filters = request.getFilters();
		if (filters.isJson())
		{
			viewBuilder.setFiltersFromJSON(filters.getJsonFilters());
		}
		else
		{
			viewBuilder.setFilters(filters.getFilters());
		}

		if (request.isUseAutoFilters())
		{
			final List<DocumentFilter> autoFilters = createAutoFilters(sqlViewBinding);
			viewBuilder.addFiltersIfAbsent(autoFilters);
		}

		if (!request.getFilterOnlyIds().isEmpty())
		{
			final String keyColumnName = sqlViewBinding.getSqlViewKeyColumnNamesMap().getSingleKeyColumnName();
			viewBuilder.addStickyFilter(DocumentFilter.inArrayFilter(keyColumnName, keyColumnName, request.getFilterOnlyIds()));
		}

		return viewBuilder.build();
	}

	private final DocumentFilter extractReferencedDocumentFilter(final WindowId targetWindowId, final DocumentPath referencedDocumentPath)
	{
		if (referencedDocumentPath == null)
		{
			return null;
		}
		else if (referencedDocumentPath.isComposedKey())
		{
			// document with composed keys does not support references
			return null;
		}
		else
		{
			final DocumentReference reference = documentReferencesService.getDocumentReference(referencedDocumentPath, targetWindowId);
			return reference.getFilter();
		}
	}

	private static List<DocumentFilter> createAutoFilters(final SqlViewBinding sqlViewBinding)
	{
		final Collection<DocumentFilterDescriptor> filters = sqlViewBinding.getViewFilterDescriptors().getAll();

		return filters
				.stream()
				.filter(DocumentFilterDescriptor::isAutoFilter)
				.map(SqlViewFactory::createAutoFilter)
				.collect(ImmutableList.toImmutableList());
	}

	private static DocumentFilter createAutoFilter(final DocumentFilterDescriptor filterDescriptor)
	{
		if (!filterDescriptor.isAutoFilter())
		{
			throw new AdempiereException("Not an auto filter: " + filterDescriptor);
		}

		final Builder filterBuilder = DocumentFilter.builder()
				.setFilterId(filterDescriptor.getFilterId());

		filterDescriptor.getParameters()
				.stream()
				.filter(DocumentFilterParamDescriptor::isAutoFilter)
				.map(SqlViewFactory::createAutoFilterParam)
				.forEach(filterBuilder::addParameter);

		return filterBuilder.build();
	}

	private static final DocumentFilterParam createAutoFilterParam(final DocumentFilterParamDescriptor filterParamDescriptor)
	{
		final Object value;
		if (filterParamDescriptor.isAutoFilterInitialValueIsDateNow())
		{
			final DocumentFieldWidgetType widgetType = filterParamDescriptor.getWidgetType();
			if (widgetType == DocumentFieldWidgetType.LocalDate)
			{
				value = SystemTime.asLocalDate();
			}
			else
			{
				value = SystemTime.asZonedDateTime();
			}
		}
		else
		{
			value = filterParamDescriptor.getAutoFilterInitialValue();
		}

		return DocumentFilterParam.builder()
				.setFieldName(filterParamDescriptor.getFieldName())
				.setOperator(Operator.EQUAL)
				.setValue(value)
				.build();
	}
}
