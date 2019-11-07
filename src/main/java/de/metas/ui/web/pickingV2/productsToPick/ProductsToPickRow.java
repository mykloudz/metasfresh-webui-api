package de.metas.ui.web.pickingV2.productsToPick;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import de.metas.handlingunits.HuId;
import de.metas.handlingunits.picking.PickingCandidate;
import de.metas.handlingunits.picking.PickingCandidateApprovalStatus;
import de.metas.handlingunits.picking.PickingCandidateId;
import de.metas.handlingunits.picking.PickingCandidatePickStatus;
import de.metas.i18n.ITranslatableString;
import de.metas.inoutcandidate.api.ShipmentScheduleId;
import de.metas.quantity.Quantity;
import de.metas.ui.web.view.IViewRow;
import de.metas.ui.web.view.ViewRowFieldNameAndJsonValues;
import de.metas.ui.web.view.ViewRowFieldNameAndJsonValuesHolder;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn.TranslationSource;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.LookupValue;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.ViewEditorRenderMode;
import de.metas.ui.web.window.descriptor.WidgetSize;
import de.metas.util.lang.CoalesceUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2018 metas GmbH
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

@ToString(exclude = "values")
public class ProductsToPickRow implements IViewRow
{
	static final String FIELD_ProductValue = "productValue";
	@ViewColumn(fieldName = FIELD_ProductValue, widgetType = DocumentFieldWidgetType.Text, captionKey = "ProductValue",
			// captionKeyIsSysConfig=true, // TODO
			widgetSize = WidgetSize.Small)
	private final String productValue;

	static final String FIELD_ProductName = "productName";
	@ViewColumn(fieldName = FIELD_ProductName, widgetType = DocumentFieldWidgetType.Text, captionKey = "ProductName", widgetSize = WidgetSize.Medium)
	private final ITranslatableString productName;

	static final String FIELD_ProductPackageSize = "productPackageSize";
	@ViewColumn(fieldName = FIELD_ProductPackageSize, widgetType = DocumentFieldWidgetType.Text, captionKey = "PackageSize", widgetSize = WidgetSize.Small)
	private final String productPackageSize;

	static final String FIELD_ProductPackageSizeUOM = "productPackageSizeUOM";
	@ViewColumn(fieldName = FIELD_ProductPackageSizeUOM, widgetType = DocumentFieldWidgetType.Text, captionKey = "Package_UOM_ID", widgetSize = WidgetSize.Small)
	private final String productPackageSizeUOM;

	static final String FIELD_Locator = "locator";
	@ViewColumn(fieldName = FIELD_Locator, widgetType = DocumentFieldWidgetType.Lookup, captionKey = "M_Locator_ID", widgetSize = WidgetSize.Small)
	private final LookupValue locator;

	static final String FIELD_LotNumber = "lotNumber";
	@ViewColumn(fieldName = FIELD_LotNumber, widgetType = DocumentFieldWidgetType.Text, //
			captionKey = ProductsToPickRowsDataFactory.ATTR_LotNumber, captionTranslationSource = TranslationSource.ATTRIBUTE_NAME, //
			widgetSize = WidgetSize.Small)
	private final String lotNumber;

	static final String FIELD_ExpiringDate = "expiringDate";
	@ViewColumn(fieldName = FIELD_ExpiringDate, widgetType = DocumentFieldWidgetType.LocalDate, //
			captionKey = ProductsToPickRowsDataFactory.ATTR_BestBeforeDate, captionTranslationSource = TranslationSource.ATTRIBUTE_NAME, //
			widgetSize = WidgetSize.Small)
	@Getter
	private final LocalDate expiringDate;

	static final String FIELD_RepackNumber = "repackNumber";
	@ViewColumn(fieldName = FIELD_RepackNumber, widgetType = DocumentFieldWidgetType.Text, //
			captionKey = ProductsToPickRowsDataFactory.ATTR_RepackNumber, captionTranslationSource = TranslationSource.ATTRIBUTE_NAME, //
			widgetSize = WidgetSize.Small)
	private final String repackNumber;

	static final String FIELD_Qty = "qty";
	@ViewColumn(fieldName = FIELD_Qty, widgetType = DocumentFieldWidgetType.Quantity, captionKey = "Qty", widgetSize = WidgetSize.Small)
	private final Quantity qty;

	static final String FIELD_QtyOverride = "qtyOverride";
	@ViewColumn(fieldName = FIELD_QtyOverride, widgetType = DocumentFieldWidgetType.Quantity, captionKey = "Qty_Override", widgetSize = WidgetSize.Small, editor = ViewEditorRenderMode.ALWAYS)
	private final Quantity qtyOverride;

	static final String FIELD_QtyReview = "qtyReview";
	@ViewColumn(fieldName = FIELD_QtyReview, widgetType = DocumentFieldWidgetType.Quantity, captionKey = "Qty", widgetSize = WidgetSize.Small, editor = ViewEditorRenderMode.ALWAYS)
	@Getter
	private final BigDecimal qtyReview;

	static final String FIELD_PickStatus = "pickStatus";
	@ViewColumn(fieldName = FIELD_PickStatus, captionKey = "PickStatus", widgetType = DocumentFieldWidgetType.List, listReferenceId = PickingCandidatePickStatus.AD_REFERENCE_ID, widgetSize = WidgetSize.Small)
	@Getter
	private final PickingCandidatePickStatus pickStatus;

	static final String FIELD_ApprovalStatus = "approvalStatus";
	@ViewColumn(fieldName = FIELD_ApprovalStatus, captionKey = "ApprovalStatus", widgetType = DocumentFieldWidgetType.List, listReferenceId = PickingCandidateApprovalStatus.AD_REFERENCE_ID, widgetSize = WidgetSize.Small)
	private final PickingCandidateApprovalStatus approvalStatus;

	//
	private final ProductsToPickRowId rowId;
	private final ProductInfo productInfo;
	@Getter
	private final boolean huReservedForThisRow;
	private boolean processed;
	@Getter
	private final ShipmentScheduleId shipmentScheduleId;
	@Getter
	@Nullable
	private final PickingCandidateId pickingCandidateId;

	//
	private final ViewRowFieldNameAndJsonValuesHolder<ProductsToPickRow> values = ViewRowFieldNameAndJsonValuesHolder.newInstance(ProductsToPickRow.class);

	@Builder(toBuilder = true)
	private ProductsToPickRow(
			@NonNull final ProductsToPickRowId rowId,
			//
			@NonNull final ProductInfo productInfo,
			final boolean huReservedForThisRow,
			//
			final LookupValue locator,
			//
			final String lotNumber,
			final LocalDate expiringDate,
			final String repackNumber,
			//
			@NonNull final Quantity qty,
			@Nullable final Quantity qtyOverride,
			@Nullable final BigDecimal qtyReview,
			//
			final PickingCandidatePickStatus pickStatus,
			final PickingCandidateApprovalStatus approvalStatus,
			final boolean processed,
			//
			@NonNull final ShipmentScheduleId shipmentScheduleId,
			final PickingCandidateId pickingCandidateId)
	{
		this.rowId = rowId;

		this.productInfo = productInfo;
		this.productValue = productInfo.getCode();
		this.productName = productInfo.getName();
		this.productPackageSize = productInfo.getPackageSize();
		this.productPackageSizeUOM = productInfo.getPackageSizeUOM();

		this.huReservedForThisRow = huReservedForThisRow;

		this.locator = locator;
		this.lotNumber = lotNumber;
		this.expiringDate = expiringDate;
		this.repackNumber = repackNumber;

		this.qty = qty;
		this.qtyOverride = qtyOverride;
		this.qtyReview = qtyReview;

		this.pickStatus = pickStatus != null ? pickStatus : PickingCandidatePickStatus.TO_BE_PICKED;
		this.approvalStatus = approvalStatus != null ? approvalStatus : PickingCandidateApprovalStatus.TO_BE_APPROVED;
		this.processed = processed;

		this.shipmentScheduleId = shipmentScheduleId;
		this.pickingCandidateId = pickingCandidateId;
	}

	@Override
	public DocumentId getId()
	{
		return rowId.toDocumentId();
	}

	@Override
	public boolean isProcessed()
	{
		return processed;
	}

	@Override
	public DocumentPath getDocumentPath()
	{
		// TODO Auto-generated method stub
		return null;
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

	public HuId getHuId()
	{
		return rowId.getHuId();
	}

	public Quantity getQtyEffective()
	{
		return CoalesceUtil.coalesce(qtyOverride, qty);
	}

	public ProductsToPickRow withUpdatesFromPickingCandidateIfNotNull(@Nullable final PickingCandidate pickingCandidate)
	{
		return pickingCandidate != null
				? withUpdatesFromPickingCandidate(pickingCandidate)
				: this;
	}

	public ProductsToPickRow withUpdatesFromPickingCandidate(@NonNull final PickingCandidate pickingCandidate)
	{
		return toBuilder()
				.qtyReview(pickingCandidate.getQtyReview())
				.pickStatus(pickingCandidate.getPickStatus())
				.approvalStatus(pickingCandidate.getApprovalStatus())
				.processed(!pickingCandidate.isDraft())
				.pickingCandidateId(pickingCandidate.getId())
				.build();
	}

	public ProductsToPickRow withQty(@NonNull final Quantity qty)
	{
		if (Objects.equals(this.qty, qty))
		{
			return this;
		}

		return toBuilder().qty(qty).build();
	}

	public ProductsToPickRow withQtyOverride(@Nullable final BigDecimal qtyOverrideBD)
	{
		final Quantity qtyOverride = qtyOverrideBD != null
				? Quantity.of(qtyOverrideBD, qty.getUOM())
				: null;

		if (Objects.equals(this.qtyOverride, qtyOverride))
		{
			return this;
		}

		return toBuilder().qtyOverride(qtyOverride).build();
	}

	public boolean isApproved()
	{
		return approvalStatus.isApproved();
	}

	private boolean isEligibleForChangingPickStatus()
	{
		return !isProcessed() && !isApproved();
	}

	public boolean isEligibleForPicking()
	{
		return isEligibleForChangingPickStatus()
				&& getHuId() != null
				&& (pickStatus.isToBePicked() || pickStatus.isPickRejected());
	}

	public boolean isEligibleForRejectPicking()
	{
		return isEligibleForChangingPickStatus()
				&& !pickStatus.isPickRejected();
	}

	public boolean isEligibleForPacking()
	{
		return !isProcessed() && isApproved() && !pickStatus.isPickRejected();
	}

	public boolean isEligibleForReview()
	{
		return !isProcessed()
				&& (pickStatus.isPicked() || pickStatus.isPickRejected());
	}

	public String getLocatorName()
	{
		return locator != null ? locator.getDisplayName() : "";
	}
}
