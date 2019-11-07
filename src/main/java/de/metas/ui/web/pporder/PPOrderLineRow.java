package de.metas.ui.web.pporder;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.compiere.model.I_C_UOM;
import org.eevolution.model.I_PP_Order;
import org.eevolution.model.I_PP_Order_BOMLine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.handlingunits.HuId;
import de.metas.handlingunits.model.I_PP_Order_Qty;
import de.metas.handlingunits.model.X_M_HU;
import de.metas.material.planning.pporder.PPOrderBOMLineId;
import de.metas.material.planning.pporder.PPOrderId;
import de.metas.product.IProductDAO;
import de.metas.product.ProductId;
import de.metas.quantity.Quantity;
import de.metas.ui.web.exceptions.EntityNotFoundException;
import de.metas.ui.web.view.IViewRow;
import de.metas.ui.web.view.IViewRowAttributes;
import de.metas.ui.web.view.IViewRowAttributesProvider;
import de.metas.ui.web.view.ViewRowFieldNameAndJsonValues;
import de.metas.ui.web.view.ViewRowFieldNameAndJsonValuesHolder;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn.ViewColumnLayout;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.uom.IUOMDAO;
import de.metas.util.Services;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

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

@ToString
public class PPOrderLineRow implements IViewRow
{
	private final DocumentPath documentPath;

	@Getter
	private final PPOrderLineRowId rowId;

	@Nullable
	private final Supplier<? extends IViewRowAttributes> attributesSupplier;

	private final List<PPOrderLineRow> includedDocuments;

	private final boolean processed;
	private final PPOrderId ppOrderId;
	private final PPOrderBOMLineId ppOrderBOMLineId;
	private final int ppOrderQtyId;

	private final HuId huId;
	private final boolean sourceHU;
	private final boolean topLevelHU;

	@Nullable
	private final String issueMethod;

	@ViewColumn(captionKey = "M_Product_ID", widgetType = DocumentFieldWidgetType.Lookup, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 10))
	private final JSONLookupValue product;

	@ViewColumn(captionKey = "Code", widgetType = DocumentFieldWidgetType.Text, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 20))
	private final String code;

	@ViewColumn(captionKey = "Type", widgetType = DocumentFieldWidgetType.Text, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 30))
	private final PPOrderLineType type;

	@ViewColumn(captionKey = "PackingInfo", widgetType = DocumentFieldWidgetType.Text, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 40))
	private final String packingInfo;

	@ViewColumn(captionKey = "QtyPlan", widgetType = DocumentFieldWidgetType.Quantity, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 50))
	private final BigDecimal qtyPlan;

	@ViewColumn(captionKey = "Qty", widgetType = DocumentFieldWidgetType.Quantity, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 60))
	private final BigDecimal qty;

	@ViewColumn(captionKey = "C_UOM_ID", widgetType = DocumentFieldWidgetType.Lookup, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 70))
	private final JSONLookupValue uom;

	@ViewColumn(captionKey = "HUStatus", widgetType = DocumentFieldWidgetType.Lookup, layouts = @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 80))
	private final JSONLookupValue huStatus;

	private final ViewRowFieldNameAndJsonValuesHolder<PPOrderLineRow> values = ViewRowFieldNameAndJsonValuesHolder.newInstance(PPOrderLineRow.class);

	public static final PPOrderLineRow cast(final IViewRow viewRecord)
	{
		return (PPOrderLineRow)viewRecord;
	}

	@lombok.Builder(builderMethodName = "builderForIssuedOrReceivedHU", builderClassName = "BuilderForIssuedOrReceivedHU")
	private PPOrderLineRow(
			@NonNull final PPOrderLineRowId rowId,
			@NonNull final PPOrderLineType type,
			@NonNull final I_PP_Order_Qty ppOrderQty,
			@NonNull final Boolean processed,
			@Nullable final Supplier<? extends IViewRowAttributes> attributesSupplier,
			@Nullable final String code, // can be null if type=HU_Storage
			@Nullable final JSONLookupValue product,
			@Nullable final String packingInfo,  // can be null if type=HU_Storage
			@NonNull final Quantity quantity,
			@NonNull final List<PPOrderLineRow> includedRows,
			@NonNull final Boolean topLevelHU,
			@NonNull final JSONLookupValue huStatus)
	{
		this.rowId = rowId;
		this.type = type;

		this.ppOrderId = PPOrderId.ofRepoId(ppOrderQty.getPP_Order_ID());
		this.ppOrderBOMLineId = PPOrderBOMLineId.ofRepoIdOrNull(ppOrderQty.getPP_Order_BOMLine_ID());
		this.huId = HuId.ofRepoId(ppOrderQty.getM_HU_ID());
		this.ppOrderQtyId = ppOrderQty.getPP_Order_Qty_ID();

		this.processed = processed;

		// Values
		this.product = product;
		this.uom = JSONLookupValueTool.createUOMLookupValue(quantity.getUOM());
		this.packingInfo = packingInfo;
		this.code = code;

		this.sourceHU = false;
		this.topLevelHU = topLevelHU;
		this.huStatus = huStatus;

		this.qtyPlan = null;

		this.attributesSupplier = attributesSupplier;

		this.includedDocuments = includedRows;

		this.qty = quantity.toBigDecimal();

		this.documentPath = computeDocumentPath();

		this.issueMethod = ppOrderQty.getPP_Order_BOMLine() == null ? null : ppOrderQty.getPP_Order_BOMLine().getIssueMethod();
	}

	@lombok.Builder(builderMethodName = "builderForPPOrder", builderClassName = "BuilderForPPOrder")
	private PPOrderLineRow(
			@NonNull final I_PP_Order ppOrder,
			@NonNull final Boolean processed,
			@Nullable final String packingInfoOrNull,
			@NonNull final IViewRowAttributesProvider attributesProvider,
			@NonNull final List<PPOrderLineRow> includedRows)
	{
		this.rowId = PPOrderLineRowId.ofPPOrderId(ppOrder.getPP_Order_ID());
		this.type = PPOrderLineType.MainProduct;

		this.ppOrderId = PPOrderId.ofRepoId(ppOrder.getPP_Order_ID());
		this.ppOrderBOMLineId = null;
		this.huId = null;
		this.ppOrderQtyId = -1;

		this.processed = processed;

		final ProductId productId = ProductId.ofRepoId(ppOrder.getM_Product_ID());
		this.product = JSONLookupValueTool.createProductLookupValue(Services.get(IProductDAO.class).getById(productId));
		final int uomId = ppOrder.getC_UOM_ID();
		this.uom = JSONLookupValueTool.createUOMLookupValue(Services.get(IUOMDAO.class).getById(uomId));
		this.packingInfo = packingInfoOrNull;
		this.code = null;

		this.sourceHU = false;
		this.topLevelHU = false;
		this.huStatus = null;

		this.qtyPlan = ppOrder.getQtyOrdered();

		this.attributesSupplier = createASIAttributesSupplier(attributesProvider,
				rowId.toDocumentId(),
				ppOrder.getM_AttributeSetInstance_ID());

		this.includedDocuments = includedRows;

		this.qty = includedDocuments.stream()
				.map(PPOrderLineRow::getQty)
				.reduce(BigDecimal.ZERO, (qtySum, includedQty) -> qtySum.add(includedQty));

		this.documentPath = computeDocumentPath();

		this.issueMethod = null;

	}

	@lombok.Builder(builderMethodName = "builderForPPOrderBomLine", builderClassName = "BuilderForPPOrderBomLine")
	private PPOrderLineRow(
			@NonNull final I_PP_Order_BOMLine ppOrderBomLine,
			@NonNull final PPOrderLineType type,
			@Nullable final String packingInfoOrNull,
			@NonNull final Boolean processed,
			@NonNull final BigDecimal qtyPlan,
			@NonNull final IViewRowAttributesProvider attributesProvider,
			@NonNull final List<PPOrderLineRow> includedRows)
	{
		this.rowId = PPOrderLineRowId.ofPPOrderBomLineId(ppOrderBomLine.getPP_Order_BOMLine_ID());

		this.type = type;

		this.ppOrderId = PPOrderId.ofRepoId(ppOrderBomLine.getPP_Order_ID());
		this.ppOrderBOMLineId = PPOrderBOMLineId.ofRepoId(ppOrderBomLine.getPP_Order_BOMLine_ID());
		this.huId = null;
		this.ppOrderQtyId = -1;

		this.processed = processed;

		this.product = JSONLookupValueTool.createProductLookupValue(ppOrderBomLine.getM_Product());
		this.uom = JSONLookupValueTool.createUOMLookupValue(ppOrderBomLine.getC_UOM());

		this.packingInfo = packingInfoOrNull;
		this.code = null;

		this.sourceHU = false;
		this.topLevelHU = false;
		this.huStatus = null;

		this.qtyPlan = qtyPlan;

		this.attributesSupplier = createASIAttributesSupplier(attributesProvider,
				rowId.toDocumentId(),
				ppOrderBomLine.getM_AttributeSetInstance_ID());

		this.includedDocuments = includedRows;

		this.qty = includedDocuments.stream()
				.map(PPOrderLineRow::getQty)
				.reduce(BigDecimal.ZERO, (qtySum, includedQty) -> qtySum.add(includedQty));

		this.documentPath = computeDocumentPath();

		this.issueMethod = ppOrderBomLine.getIssueMethod();
	}

	@lombok.Builder(builderMethodName = "builderForSourceHU", builderClassName = "BuilderForSourceHU")
	private PPOrderLineRow(
			@NonNull final PPOrderLineRowId rowId,
			@NonNull final PPOrderLineType type,
			@NonNull final HuId huId,
			@Nullable final Supplier<? extends IViewRowAttributes> attributesSupplier,
			@NonNull final String code,
			@NonNull final JSONLookupValue product,
			@NonNull final String packingInfo,
			@NonNull final JSONLookupValue uom,
			@NonNull final BigDecimal qty,
			@NonNull final Boolean topLevelHU,
			@NonNull final JSONLookupValue huStatus)
	{
		this.rowId = rowId;
		this.type = type;

		this.ppOrderId = null;
		this.ppOrderBOMLineId = null;
		this.huId = huId;
		this.ppOrderQtyId = -1;

		this.processed = true;

		// Values
		this.product = product;
		this.uom = uom;
		this.packingInfo = packingInfo;
		this.code = code;

		this.sourceHU = true;
		this.topLevelHU = topLevelHU;
		this.huStatus = huStatus;

		this.qtyPlan = null;

		this.attributesSupplier = attributesSupplier;

		this.includedDocuments = ImmutableList.of();

		this.qty = qty;

		this.documentPath = computeDocumentPath();

		this.issueMethod = null;
	}

	private DocumentPath computeDocumentPath()
	{
		if (type == PPOrderLineType.MainProduct)
		{
			return DocumentPath.rootDocumentPath(PPOrderConstants.AD_WINDOW_ID_PP_Order, DocumentId.of(ppOrderId));
		}
		else if (type.isBOMLine())
		{
			return DocumentPath.includedDocumentPath(PPOrderConstants.AD_WINDOW_ID_PP_Order, DocumentId.of(ppOrderId), PPOrderConstants.TABID_ID_PP_Order_BOMLine, DocumentId.of(ppOrderBOMLineId));
		}
		else if (type.isHUOrHUStorage())
		{
			// Better return null because we don't want to have here all processes which are related to HUs.
			// More, in case the HU is destroyed, that HU will not be found in the standard HU Editor View so no process will be executed.
			// see https://github.com/metasfresh/metasfresh-webui-api/issues/1097#issuecomment-436944470, problem 2.
			// return DocumentPath.rootDocumentPath(WEBUI_HU_Constants.WEBUI_HU_Window_ID, DocumentId.of(huId));
			return null;
		}
		else
		{
			throw new IllegalStateException("Unknown type: " + type);
		}
	}

	@Nullable
	private static final Supplier<IViewRowAttributes> createASIAttributesSupplier(
			@NonNull final IViewRowAttributesProvider asiAttributesProvider,
			@NonNull final DocumentId documentId,
			final int asiId)
	{
		if (asiId > 0)
		{
			return () -> asiAttributesProvider.getAttributes(documentId, DocumentId.of(asiId));
		}
		else
		{
			return null;
		}
	}

	public PPOrderId getOrderId()
	{
		return ppOrderId;
	}

	public PPOrderBOMLineId getOrderBOMLineId()
	{
		return ppOrderBOMLineId;
	}

	public int getPP_Order_Qty_ID()
	{
		return ppOrderQtyId;
	}

	@Override
	public DocumentId getId()
	{
		return rowId.toDocumentId();
	}

	@Override
	public ImmutableSet<String> getFieldNames()
	{
		return values.getFieldNames();
	}

	@Override
	public ViewRowFieldNameAndJsonValues getFieldNameAndJsonValues()
	{
		return values.get(this);
	}

	@Override
	public boolean isProcessed()
	{
		return processed;
	}

	@Override
	public PPOrderLineType getType()
	{
		return type;
	}

	@Override
	public DocumentPath getDocumentPath()
	{
		return documentPath;
	}

	public JSONLookupValue getProduct()
	{
		return product;
	}

	public ProductId getProductId()
	{
		final JSONLookupValue product = getProduct();
		return product != null ? ProductId.ofRepoIdOrNull(product.getKeyAsInt()) : null;

	}

	private int getUomId()
	{
		return uom == null ? -1 : uom.getKeyAsInt();
	}

	public I_C_UOM getUom()
	{
		final int uomId = getUomId();
		return Services.get(IUOMDAO.class).getById(uomId);
	}

	public HuId getHuId()
	{
		return huId;
	}

	public String getPackingInfo()
	{
		return packingInfo;
	}

	public BigDecimal getQty()
	{
		return qty;
	}

	public BigDecimal getQtyPlan()
	{
		return qtyPlan;
	}

	public boolean isReceipt()
	{
		return getType().canReceive();
	}

	public boolean isIssue()
	{
		return getType().canIssue();
	}

	public boolean isSourceHU()
	{
		return sourceHU;
	}

	public boolean isTopLevelHU()
	{
		return topLevelHU;
	}

	public boolean isHUStatusActive()
	{
		return huStatus != null && X_M_HU.HUSTATUS_Active.equals(huStatus.getKey());
	}

	public String getIssueMethod()
	{
		return issueMethod;
	}

	@Override
	public List<PPOrderLineRow> getIncludedRows()
	{
		return includedDocuments;
	}

	@Override
	public boolean hasAttributes()
	{
		return attributesSupplier != null;
	}

	@Override
	public IViewRowAttributes getAttributes() throws EntityNotFoundException
	{
		if (attributesSupplier == null)
		{
			throw new EntityNotFoundException("This PPOrderLineRow does not support attributes; this=" + this);
		}

		final IViewRowAttributes attributes = attributesSupplier.get();
		if (attributes == null)
		{
			throw new EntityNotFoundException("This PPOrderLineRow does not support attributes; this=" + this);
		}
		return attributes;
	}

}
