package de.metas.ui.web.pporder;

import static org.adempiere.model.InterfaceWrapperHelper.load;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.lang.impl.TableRecordReferenceSet;
import org.compiere.util.Evaluatee;
import org.eevolution.api.IPPOrderDAO;
import org.eevolution.api.PPOrderPlanningStatus;
import org.eevolution.model.I_PP_Order;
import org.eevolution.model.I_PP_Order_BOMLine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.i18n.ITranslatableString;
import de.metas.material.planning.pporder.IPPOrderBOMDAO;
import de.metas.material.planning.pporder.PPOrderId;
import de.metas.order.OrderLineId;
import de.metas.process.RelatedProcessDescriptor;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.exceptions.EntityNotFoundException;
import de.metas.ui.web.view.IView;
import de.metas.ui.web.view.IViewRow;
import de.metas.ui.web.view.ViewId;
import de.metas.ui.web.view.ViewResult;
import de.metas.ui.web.view.ViewRowsOrderBy;
import de.metas.ui.web.view.event.ViewChangesCollector;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentIdsSelection;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import de.metas.ui.web.window.model.DocumentQueryOrderBy;
import de.metas.ui.web.window.model.sql.SqlOptions;
import de.metas.util.GuavaCollectors;
import de.metas.util.Services;
import lombok.Builder;
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

public class PPOrderLinesView implements IView
{
	private final ViewId parentViewId;
	private final DocumentId parentRowId;

	private final ViewId viewId;
	private final JSONViewDataType viewType;
	private final ImmutableSet<DocumentPath> referencingDocumentPaths;

	private final PPOrderId ppOrderId;
	private final OrderLineId salesOrderLineId;

	private final PPOrderLinesViewDataSupplier dataSupplier;

	final List<RelatedProcessDescriptor> additionalRelatedProcessDescriptors;

	public static PPOrderLinesView cast(final IView view)
	{
		return (PPOrderLinesView)view;
	}

	@Builder
	private PPOrderLinesView(
			final ViewId parentViewId,
			final DocumentId parentRowId,
			@NonNull final ViewId viewId,
			@NonNull final JSONViewDataType viewType,
			final Set<DocumentPath> referencingDocumentPaths,
			@NonNull final PPOrderId ppOrderId,
			final PPOrderLinesViewDataSupplier dataSupplier,
			@NonNull final List<RelatedProcessDescriptor> additionalRelatedProcessDescriptors)
	{
		this.parentViewId = parentViewId; // might be null
		this.parentRowId = parentRowId; // might be null
		this.viewId = viewId;
		this.viewType = viewType;
		this.referencingDocumentPaths = referencingDocumentPaths == null ? ImmutableSet.of() : ImmutableSet.copyOf(referencingDocumentPaths);

		this.additionalRelatedProcessDescriptors = ImmutableList.copyOf(additionalRelatedProcessDescriptors);

		this.ppOrderId = ppOrderId;
		final I_PP_Order ppOrder = load(ppOrderId, I_PP_Order.class);
		this.salesOrderLineId = OrderLineId.ofRepoIdOrNull(ppOrder.getC_OrderLine_ID());

		this.dataSupplier = dataSupplier;
	}

	@Override
	public ITranslatableString getDescription()
	{
		return getData().getDescription();
	}

	public PPOrderPlanningStatus getPlanningStatus()
	{
		return getData().getPlanningStatus();
	}

	public boolean isStatusPlanning()
	{
		return PPOrderPlanningStatus.PLANNING.equals(getPlanningStatus());
	}

	public boolean isStatusReview()
	{
		return PPOrderPlanningStatus.REVIEW.equals(getPlanningStatus());
	}

	@Override
	public ViewId getParentViewId()
	{
		return parentViewId;
	}

	@Override
	public DocumentId getParentRowId()
	{
		return parentRowId;
	}

	@Override
	public ViewId getViewId()
	{
		return viewId;
	}

	@Override
	public JSONViewDataType getViewType()
	{
		return viewType;
	}

	@Override
	public ImmutableSet<DocumentPath> getReferencingDocumentPaths()
	{
		return referencingDocumentPaths;
	}

	/**
	 * @param may be {@code null}; in that case, the method also returns {@code null}
	 * @return the table name for the given row
	 */
	@Override
	public String getTableNameOrNull(@Nullable final DocumentId documentId)
	{
		if (documentId == null)
		{
			return null;
		}
		final PPOrderLineRow ppOrderLine = getById(documentId);
		if (ppOrderLine == null)
		{
			return null; // just be sure to avoid an NPE in here
		}
		return ppOrderLine.getType().getTableName();
	}

	public PPOrderId getPpOrderId()
	{
		return ppOrderId;
	}

	public OrderLineId getSalesOrderLineId()
	{
		return salesOrderLineId;
	}

	@Override
	public long size()
	{
		return getData().size();
	}

	@Override
	public void afterDestroy()
	{
		invalidateAllNoNotify();
	}

	@Override
	public int getQueryLimit()
	{
		return -1;
	}

	@Override
	public boolean isQueryLimitHit()
	{
		return false;
	}

	@Override
	public ViewResult getPage(
			final int firstRow, 
			final int pageLength, 
			@NonNull final ViewRowsOrderBy orderBys)
	{
		final Stream<PPOrderLineRow> stream = getData().stream()
				.skip(firstRow)
				.limit(pageLength);

		final List<IViewRow> page = stream.collect(GuavaCollectors.toImmutableList());

		return ViewResult.ofViewAndPage(this, firstRow, pageLength, orderBys.toDocumentQueryOrderByList(), page);
	}

	@Override
	public PPOrderLineRow getById(final DocumentId documentId) throws EntityNotFoundException
	{
		final PPOrderLineRowId ppOrderLineRowId = PPOrderLineRowId.fromDocumentId(documentId);
		return getData().getById(ppOrderLineRowId);
	}

	@Override
	public LookupValuesList getFilterParameterDropdown(final String filterId, final String filterParameterName, final Evaluatee ctx)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public LookupValuesList getFilterParameterTypeahead(final String filterId, final String filterParameterName, final String query, final Evaluatee ctx)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public List<DocumentFilter> getStickyFilters()
	{
		return ImmutableList.of();
	}

	@Override
	public List<DocumentFilter> getFilters()
	{
		return ImmutableList.of();
	}

	@Override
	public List<DocumentQueryOrderBy> getDefaultOrderBys()
	{
		return ImmutableList.of();
	}

	@Override
	public String getSqlWhereClause(final DocumentIdsSelection viewDocumentIds, final SqlOptions sqlOpts)
	{
		return null; // not supported
	}

	@Override
	public boolean hasAttributesSupport()
	{
		return true;
	}

	@Override
	public <T> List<T> retrieveModelsByIds(
			@NonNull final DocumentIdsSelection documentIds,
			@NonNull final Class<T> modelClass)
	{
		return streamByIds(documentIds)
				.map(ppOrderLineRow -> getModel(ppOrderLineRow, modelClass))
				.filter(optional -> optional.isPresent())
				.map(optional -> optional.get())
				.collect(Collectors.toList());
	}

	/**
	 * loads and returns the given {@code ppOrderLineRow}'s {@code PP_Order} or {@code P_Order_BOMLine}, if available.
	 *
	 * @param ppOrderLineRow
	 * @param modelClass
	 * @return
	 */
	private <T> Optional<T> getModel(
			@NonNull final PPOrderLineRow ppOrderLineRow,
			@NonNull final Class<T> modelClass)
	{
		if (I_PP_Order.class.isAssignableFrom(modelClass))
		{
			if (ppOrderLineRow.getOrderId() == null)
			{
				return Optional.empty();
			}
			else
			{
				final I_PP_Order order = Services.get(IPPOrderDAO.class).getById(ppOrderLineRow.getOrderId());
				return Optional.of(InterfaceWrapperHelper.create(order, modelClass));
			}
		}
		else if (I_PP_Order_BOMLine.class.isAssignableFrom(modelClass))
		{
			if (ppOrderLineRow.getOrderBOMLineId() == null)
			{
				return Optional.empty();
			}
			else
			{
				final I_PP_Order_BOMLine orderBOMLine = Services.get(IPPOrderBOMDAO.class).getOrderBOMLineById(ppOrderLineRow.getOrderBOMLineId());
				return Optional.of(InterfaceWrapperHelper.create(orderBOMLine, modelClass));
			}
		}
		else
		{
			return Optional.empty();
		}
	}

	@Override
	public Stream<PPOrderLineRow> streamByIds(final DocumentIdsSelection documentIds)
	{
		return getData().streamByIds(documentIds);
	}

	/** @return top level rows and included rows recursive stream */
	public Stream<PPOrderLineRow> streamAllRecursive()
	{
		return getData().streamRecursive();
	}

	@Override
	public void notifyRecordsChanged(final TableRecordReferenceSet recordRefs)
	{
		// TODO: notifyRecordsChanged: identify the sub-trees which could be affected and invalidate only those
	}

	@Override
	public List<RelatedProcessDescriptor> getAdditionalRelatedProcessDescriptors()
	{
		return additionalRelatedProcessDescriptors;
	}

	@Override
	public void invalidateAll()
	{
		invalidateAllNoNotify();

		ViewChangesCollector.getCurrentOrAutoflush()
				.collectFullyChanged(this);
	}

	private void invalidateAllNoNotify()
	{
		dataSupplier.invalidate();
	}

	private PPOrderLinesViewData getData()
	{
		return dataSupplier.getData();
	}

}
