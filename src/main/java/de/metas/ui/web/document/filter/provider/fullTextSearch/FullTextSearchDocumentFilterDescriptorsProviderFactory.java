package de.metas.ui.web.document.filter.provider.fullTextSearch;

import java.util.Collection;

import javax.annotation.Nullable;

import org.adempiere.ad.element.api.AdTabId;
import org.elasticsearch.client.Client;
import org.springframework.stereotype.Component;

import de.metas.elasticsearch.indexer.IESModelIndexer;
import de.metas.elasticsearch.indexer.IESModelIndexersRegistry;
import de.metas.i18n.IMsgBL;
import de.metas.i18n.ITranslatableString;
import de.metas.ui.web.document.filter.DocumentFilterDescriptor;
import de.metas.ui.web.document.filter.DocumentFilterInlineRenderMode;
import de.metas.ui.web.document.filter.DocumentFilterParamDescriptor;
import de.metas.ui.web.document.filter.provider.DocumentFilterDescriptorsProvider;
import de.metas.ui.web.document.filter.provider.DocumentFilterDescriptorsProviderFactory;
import de.metas.ui.web.document.filter.provider.ImmutableDocumentFilterDescriptorsProvider;
import de.metas.ui.web.document.filter.provider.NullDocumentFilterDescriptorsProvider;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.util.Services;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2019 metas GmbH
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

@Component
public class FullTextSearchDocumentFilterDescriptorsProviderFactory implements DocumentFilterDescriptorsProviderFactory
{
	private static final String MSG_FULL_TEXT_SEARCH_CAPTION = "Search";

	// services
	private final transient IMsgBL msgBL = Services.get(IMsgBL.class);
	private final IESModelIndexersRegistry esModelIndexersRegistry = Services.get(IESModelIndexersRegistry.class);
	private final Client elasticsearchClient;

	public FullTextSearchDocumentFilterDescriptorsProviderFactory(
			@NonNull final org.elasticsearch.client.Client elasticsearchClient)
	{
		this.elasticsearchClient = elasticsearchClient;
	}

	@Override
	public DocumentFilterDescriptorsProvider createFiltersProvider(
			@Nullable final AdTabId adTabId_NOTUSED,
			@Nullable final String tableName,
			@Nullable final Collection<DocumentFieldDescriptor> fields_NOTUSED)
	{
		if (tableName == null)
		{
			return NullDocumentFilterDescriptorsProvider.instance;
		}

		final IESModelIndexer modelIndexer = esModelIndexersRegistry.getFullTextSearchModelIndexer(tableName)
				.orElse(null);
		if (modelIndexer == null)
		{
			return NullDocumentFilterDescriptorsProvider.instance;
		}

		final ITranslatableString caption = msgBL.getTranslatableMsgText(MSG_FULL_TEXT_SEARCH_CAPTION);
		final FullTextSearchFilterContext context = createFullTextSearchFilterContext(modelIndexer);

		final DocumentFilterDescriptor filterDescriptor = DocumentFilterDescriptor.builder()
				.setFilterId(FullTextSearchSqlDocumentFilterConverter.FILTER_ID)
				.setDisplayName(caption)
				.setFrequentUsed(true)
				.setInlineRenderMode(DocumentFilterInlineRenderMode.INLINE_PARAMETERS)
				.addParameter(DocumentFilterParamDescriptor.builder()
						.setFieldName(FullTextSearchSqlDocumentFilterConverter.PARAM_SearchText)
						.setDisplayName(caption)
						.setWidgetType(DocumentFieldWidgetType.Text))
				.addInternalParameter(FullTextSearchSqlDocumentFilterConverter.PARAM_Context, context)
				.build();

		return ImmutableDocumentFilterDescriptorsProvider.of(filterDescriptor);
	}

	private FullTextSearchFilterContext createFullTextSearchFilterContext(final IESModelIndexer modelIndexer)
	{
		return FullTextSearchFilterContext.builder()
				.elasticsearchClient(elasticsearchClient)
				.modelTableName(modelIndexer.getModelTableName())
				.esIndexName(modelIndexer.getIndexName())
				.esSearchFieldNames(modelIndexer.getFullTextSearchFieldNames())
				.build();
	}

}
