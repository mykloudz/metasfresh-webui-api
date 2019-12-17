package de.metas.ui.web.window.invalidation;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.ad.trx.api.OnTrxMissingPolicy;
import org.adempiere.util.lang.IAutoCloseable;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.adempiere.util.lang.impl.TableRecordReferenceSet;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import de.metas.cache.CacheMgt;
import de.metas.cache.ICacheResetListener;
import de.metas.cache.model.CacheInvalidateMultiRequest;
import de.metas.cache.model.CacheInvalidateRequest;
import de.metas.logging.LogManager;
import de.metas.ui.web.view.IViewsRepository;
import de.metas.ui.web.window.model.DocumentCollection;
import de.metas.util.Services;
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
 * This component listens to all cache invalidation events (see {@link CacheMgt}) and invalidates the right documents or included documents from {@link DocumentCollection}.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
@Component
public class DocumentCacheInvalidationDispatcher implements ICacheResetListener
{
	private static final Logger logger = LogManager.getLogger(DocumentCacheInvalidationDispatcher.class);
	private final DocumentCollection documents;
	private final IViewsRepository viewsRepository;
	private final Executor async;

	public DocumentCacheInvalidationDispatcher(
			@NonNull final DocumentCollection documents,
			@NonNull final IViewsRepository viewsRepository)
	{
		this.documents = documents;
		this.viewsRepository = viewsRepository;
		async = createAsyncExecutor();
	}

	private static Executor createAsyncExecutor()
	{
		final CustomizableThreadFactory asyncThreadFactory = new CustomizableThreadFactory(DocumentCacheInvalidationDispatcher.class.getSimpleName());
		asyncThreadFactory.setDaemon(true);

		return Executors.newSingleThreadExecutor(asyncThreadFactory);
	}

	@PostConstruct
	private void postConstruct()
	{
		CacheMgt.get().addCacheResetListener(this);
	}

	@Override
	public long reset(@NonNull final CacheInvalidateMultiRequest request)
	{
		final ITrxManager trxManager = Services.get(ITrxManager.class);

		final ITrx currentTrx = trxManager.getThreadInheritedTrx(OnTrxMissingPolicy.ReturnTrxNone);
		if (trxManager.isActive(currentTrx))
		{
			final CacheInvalidateMultiRequestsCollector collector = currentTrx.getPropertyAndProcessAfterCommit(
					CacheInvalidateMultiRequestsCollector.class.getName(),
					() -> new CacheInvalidateMultiRequestsCollector(currentTrx.getTrxName()),
					CacheInvalidateMultiRequestsCollector::resetAsync);

			collector.collect(request);
		}
		else
		{
			new CacheInvalidateMultiRequestsCollector()
					.collect(request)
					.resetAsync();
		}

		return 1; // not relevant
	}

	private void resetAsync(@NonNull final DocumentToInvalidateMap documentsToInvalidate)
	{
		logger.trace("resetAsync: {}", documentsToInvalidate);
		async.execute(() -> resetNow(documentsToInvalidate));
	}

	private void resetNow(@NonNull final DocumentToInvalidateMap documentsToInvalidate)
	{
		logger.trace("resetNow: {}", documentsToInvalidate);

		try (final IAutoCloseable c = documents.getWebsocketPublisher().temporaryCollectOnThisThread())
		{
			documentsToInvalidate.toCollection().forEach(documents::invalidate);
		}

		//
		final TableRecordReferenceSet rootRecords = documentsToInvalidate.getRootRecords();
		viewsRepository.notifyRecordsChanged(rootRecords);
	}

	private final class CacheInvalidateMultiRequestsCollector
	{
		private final String name; // used for debugging
		private DocumentToInvalidateMap documents = new DocumentToInvalidateMap();

		private CacheInvalidateMultiRequestsCollector(final String name)
		{
			this.name = name;
		}

		private CacheInvalidateMultiRequestsCollector()
		{
			this.name = "-";
		}

		public CacheInvalidateMultiRequestsCollector collect(@NonNull final CacheInvalidateMultiRequest multiRequest)
		{
			multiRequest.getRequests().forEach(this::collect);
			return this;
		}

		private void collect(@NonNull final CacheInvalidateRequest request)
		{
			logger.trace("Collecting request on `{}`: {}", name, request);

			final TableRecordReference rootDocumentRef = request.getRootRecordOrNull();
			if (rootDocumentRef == null)
			{
				return;
			}

			final DocumentToInvalidate documentToInvalidate = documents.getDocumentToInvalidate(rootDocumentRef);

			final String childTableName = request.getChildTableName();
			if (childTableName == null)
			{
				documentToInvalidate.invalidateDocument();
			}
			else if (request.isAllRecords())
			{
				documentToInvalidate.invalidateAllIncludedDocuments(childTableName);

				// NOTE: as a workaround to solve the problem of https://github.com/metasfresh/metasfresh-webui-api/issues/851,
				// we are invalidating the whole root document to make sure that in case there were any virtual columns on header,
				// those get refreshed too.
				documentToInvalidate.invalidateDocument();
			}
			else
			{
				final int childRecordId = request.getChildRecordId();
				documentToInvalidate.addIncludedDocument(childTableName, childRecordId);
			}
		}

		public void resetAsync()
		{
			final DocumentToInvalidateMap documents = this.documents;
			this.documents = null; // just to prevent adding more events

			logger.trace("Flushing {} collected requests for on `{}`", documents.size(), name);

			if (documents.isEmpty())
			{
				return;
			}

			DocumentCacheInvalidationDispatcher.this.resetAsync(documents);
		}
	}
}
