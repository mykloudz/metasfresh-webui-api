package de.metas.ui.web.view;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.lang.IAutoCloseable;
import org.adempiere.util.lang.MutableInt;
import org.adempiere.util.lang.impl.TableRecordReferenceSet;
import org.compiere.Adempiere;
import org.compiere.util.DB;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import de.metas.logging.LogManager;
import de.metas.ui.web.base.model.I_T_WEBUI_ViewSelection;
import de.metas.ui.web.base.model.I_T_WEBUI_ViewSelectionLine;
import de.metas.ui.web.exceptions.EntityNotFoundException;
import de.metas.ui.web.menu.MenuNode;
import de.metas.ui.web.menu.MenuTreeRepository;
import de.metas.ui.web.session.UserSession;
import de.metas.ui.web.view.descriptor.ViewLayout;
import de.metas.ui.web.view.event.ViewChangesCollector;
import de.metas.ui.web.view.json.JSONFilterViewRequest;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.controller.DocumentPermissionsHelper;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.util.Check;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2016 metas GmbH
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

@Repository
public class ViewsRepository implements IViewsRepository
{
	private static final Logger logger = LogManager.getLogger(ViewsRepository.class);

	private final ImmutableMap<ViewFactoryKey, IViewFactory> factories;
	@Autowired
	private SqlViewFactory defaultFactory;

	@Autowired
	private MenuTreeRepository menuTreeRepo;

	@Value("${metasfresh.webui.view.truncateOnStartUp:true}")
	private boolean truncateSelectionOnStartUp;

	private final ImmutableMap<WindowId, IViewsIndexStorage> viewsIndexStorages;
	private final IViewsIndexStorage defaultViewsIndexStorage = new DefaultViewsRepositoryStorage();

	/**
	 * 
	 * @param neededForDBAccess not used in here, but we need to cause spring to initialize it <b>before</b> this component can be initialized.
	 *            So, if you clean this up, please make sure that the webui-API still starts up ^^.
	 */
	public ViewsRepository(
			@NonNull final Adempiere neededForDBAccess,
			@NonNull final List<IViewFactory> viewFactories,
			@NonNull final Optional<List<IViewsIndexStorage>> viewIndexStorages)
	{
		factories = createFactoriesMap(viewFactories);
		factories.values().forEach(viewFactory -> viewFactory.setViewsRepository(this));
		logger.info("Registered following view factories: {}", factories);

		this.viewsIndexStorages = createViewIndexStoragesMap(viewIndexStorages.orElseGet(ImmutableList::of));
		this.viewsIndexStorages.values().forEach(viewsIndexStorage -> viewsIndexStorage.setViewsRepository(this));
		logger.info("Registered following view index storages: {}", this.viewsIndexStorages);
	}

	@PostConstruct
	private void truncateTempTablesIfAllowed()
	{
		if (truncateSelectionOnStartUp)
		{
			truncateTable(I_T_WEBUI_ViewSelection.Table_Name);
			truncateTable(I_T_WEBUI_ViewSelectionLine.Table_Name);
		}
		else
		{
			logger.info("Skip truncating selection tables on startup because not configured");
		}
	}

	private static void truncateTable(final String tableName)
	{
		final Stopwatch stopwatch = Stopwatch.createStarted();
		try
		{
			final int no = DB.executeUpdateEx("TRUNCATE TABLE " + tableName, ITrx.TRXNAME_NoneNotNull);
			logger.info("Deleted {} records(all) from table {} (Took: {})", no, tableName, stopwatch);
		}
		catch (final Exception ex)
		{
			logger.warn("Failed deleting all from {} (Took: {})", tableName, stopwatch, ex);
		}
	}

	private static ImmutableMap<ViewFactoryKey, IViewFactory> createFactoriesMap(final Collection<IViewFactory> viewFactories)
	{
		final Map<ViewFactoryKey, IViewFactory> factories = new HashMap<>();
		for (final IViewFactory factory : viewFactories)
		{
			final ViewFactory annotation = factory.getClass().getAnnotation(ViewFactory.class);
			if (annotation == null)
			{
				// Don't need to warn about this one. It's a known case.
				if (!SqlViewFactory.class.equals(factory.getClass()))
				{
					// this might be a development bug
					logger.warn("Skip {} because it's not annotated with {}", factory, ViewFactory.class);
				}
				continue;
			}

			final WindowId windowId = WindowId.fromJson(annotation.windowId());
			JSONViewDataType[] viewTypes = annotation.viewTypes();
			if (viewTypes.length == 0)
			{
				viewTypes = JSONViewDataType.values();
			}

			for (final JSONViewDataType viewType : viewTypes)
			{
				factories.put(ViewFactoryKey.of(windowId, viewType), factory);
			}
		}

		return ImmutableMap.copyOf(factories);
	}

	private static ImmutableMap<WindowId, IViewsIndexStorage> createViewIndexStoragesMap(List<IViewsIndexStorage> viewsIndexStorages)
	{
		final ImmutableMap.Builder<WindowId, IViewsIndexStorage> map = ImmutableMap.builder();

		for (final IViewsIndexStorage viewsIndexStorage : viewsIndexStorages)
		{
			if (viewsIndexStorage instanceof DefaultViewsRepositoryStorage)
			{
				logger.warn("Skipping {} because it shall not be in spring context", viewsIndexStorage);
				continue;
			}

			final WindowId windowId = viewsIndexStorage.getWindowId();
			Check.assumeNotNull(windowId, "{} shall not have windowId null", viewsIndexStorage);

			map.put(windowId, viewsIndexStorage);
		}

		return map.build();
	}

	private final IViewFactory getFactory(final WindowId windowId, final JSONViewDataType viewType)
	{
		IViewFactory factory = factories.get(ViewFactoryKey.of(windowId, viewType));
		if (factory != null)
		{
			return factory;
		}

		factory = factories.get(ViewFactoryKey.of(windowId, null));
		if (factory != null)
		{
			return factory;
		}

		return defaultFactory;
	}

	@Override
	public IViewsIndexStorage getViewsStorageFor(@NonNull final ViewId viewId)
	{
		final IViewsIndexStorage viewIndexStorage = viewsIndexStorages.get(viewId.getWindowId());
		if (viewIndexStorage != null)
		{
			return viewIndexStorage;
		}

		return defaultViewsIndexStorage;
	}

	private Stream<IView> streamAllViews()
	{
		return Streams.concat(viewsIndexStorages.values().stream(), Stream.of(defaultViewsIndexStorage))
				.flatMap(IViewsIndexStorage::streamAllViews);
	}

	@Override
	public List<ViewProfile> getAvailableProfiles(final WindowId windowId, final JSONViewDataType viewDataType)
	{
		final IViewFactory factory = getFactory(windowId, viewDataType);
		return factory.getAvailableProfiles(windowId);
	}

	@Override
	public ViewLayout getViewLayout(final WindowId windowId, final JSONViewDataType viewDataType, final ViewProfileId profileId)
	{
		final String viewId = null; // N/A
		DocumentPermissionsHelper.assertViewAccess(windowId, viewId, UserSession.getCurrentPermissions());

		final IViewFactory factory = getFactory(windowId, viewDataType);
		return factory.getViewLayout(windowId, viewDataType, profileId)
				// Enable AllowNew if we have a menu node to create new records
				.withAllowNewRecordIfPresent(menuTreeRepo.getUserSessionMenuTree()
						.getNewRecordNodeForWindowId(windowId)
						.map(MenuNode::getCaption));
	}

	@Override
	public List<IView> getViews()
	{
		return streamAllViews().collect(ImmutableList.toImmutableList());
	}

	@Override
	public IView createView(final CreateViewRequest request)
	{
		logger.trace("Creating new view from {}", request);

		final WindowId windowId = request.getViewId().getWindowId();
		final JSONViewDataType viewType = request.getViewType();
		final IViewFactory factory = getFactory(windowId, viewType);
		final IView view = factory.createView(request);
		if (view == null)
		{
			throw new AdempiereException("Failed creating view")
					.setParameter("request", request)
					.setParameter("factory", factory.toString());
		}

		getViewsStorageFor(view.getViewId()).put(view);
		logger.trace("Created view {}", view);

		return view;
	}

	@Override
	public IView filterView(final ViewId viewId, final JSONFilterViewRequest jsonRequest)
	{
		logger.trace("Creating filtered view from {} using {}", viewId, jsonRequest);

		// Get current view
		final IView view = getView(viewId);

		//
		// Create the new view
		final IViewFactory factory = getFactory(view.getViewId().getWindowId(), view.getViewType());
		final IView newView = factory.filterView(view, jsonRequest, () -> this);
		if (newView == null)
		{
			throw new AdempiereException("Failed filtering view")
					.setParameter("viewId", viewId)
					.setParameter("request", jsonRequest)
					.setParameter("factory", factory.toString());
		}

		//
		// Add the new view to our internal map
		// NOTE: avoid adding if the factory returned the same view.
		if (view != newView)
		{
			getViewsStorageFor(newView.getViewId()).put(newView);
			logger.trace("Created filtered view {}", newView);
		}
		else
		{
			logger.trace("Filtered view is the same as the ordiginal. Returning the original {}", view);
		}

		// Return the newly created view
		return newView;
	}

	@Override
	public IView deleteStickyFilter(final ViewId viewId, final String filterId)
	{
		logger.trace("Deleting sticky filter {} from {}", filterId, viewId);

		// Get current view
		final IView view = getView(viewId);

		//
		// Create the new view
		final IViewFactory factory = getFactory(view.getViewId().getWindowId(), view.getViewType());
		final IView newView = factory.deleteStickyFilter(view, filterId);
		if (newView == null)
		{
			throw new AdempiereException("Failed deleting sticky/static filter")
					.setParameter("viewId", viewId)
					.setParameter("filterId", filterId)
					.setParameter("factory", factory.toString());
		}

		//
		// Add the new view to our internal map
		// NOTE: avoid adding if the factory returned the same view.
		if (view != newView)
		{
			getViewsStorageFor(newView.getViewId()).put(newView);
			logger.trace("Sticky filter deleted. Returning new view {}", newView);
		}
		else
		{
			logger.trace("Sticky filter NOT deleted. Returning current view {}", view);
		}

		// Return the newly created view
		return newView;
	}

	@Override
	public IView getView(@NonNull final String viewIdStr)
	{
		final ViewId viewId = ViewId.ofViewIdString(viewIdStr);
		final IView view = getViewIfExists(viewId);
		if (view == null)
		{
			throw new EntityNotFoundException("No view found for viewId=" + viewId);
		}
		return view;
	}

	@Override
	public IView getViewIfExists(final ViewId viewId)
	{
		final IView view = getViewsStorageFor(viewId).getByIdOrNull(viewId);
		if (view == null)
		{
			throw new EntityNotFoundException("View not found: " + viewId.toJson());
		}

		DocumentPermissionsHelper.assertViewAccess(viewId.getWindowId(), viewId.getViewId(), UserSession.getCurrentPermissions());

		return view;
	}

	@Override
	public void closeView(@NonNull final ViewId viewId, @NonNull final ViewCloseAction closeAction)
	{
		getViewsStorageFor(viewId).closeById(viewId, closeAction);
		logger.trace("Closed/Removed view {} using close action {}", viewId, closeAction);
	}

	@Override
	public void invalidateView(final ViewId viewId)
	{
		getViewsStorageFor(viewId).invalidateView(viewId);
		logger.trace("Invalided view {}", viewId);
	}

	@Override
	public void invalidateView(final IView view)
	{
		invalidateView(view.getViewId());
	}

	@Override
	@Async
	public void notifyRecordsChanged(@NonNull final TableRecordReferenceSet recordRefs)
	{
		if (recordRefs.isEmpty())
		{
			logger.trace("No changed records provided. Skip notifying views.");
			return;
		}

		try (final IAutoCloseable c = ViewChangesCollector.currentOrNewThreadLocalCollector())
		{
			final MutableInt notifiedCount = MutableInt.zero();
			streamAllViews()
					.forEach(view -> {
						try
						{
							view.notifyRecordsChanged(recordRefs);
							notifiedCount.incrementAndGet();
						}
						catch (final Exception ex)
						{
							logger.warn("Failed calling notifyRecordsChanged for view={} with recordRefs={}. Ignored.", view, recordRefs, ex);
						}
					});

			logger.debug("Notified {} views about changed records: {}", notifiedCount, recordRefs);
		}
	}

	@lombok.Value(staticConstructor = "of")
	private static class ViewFactoryKey
	{
		WindowId windowId;
		JSONViewDataType viewType;
	}
}
