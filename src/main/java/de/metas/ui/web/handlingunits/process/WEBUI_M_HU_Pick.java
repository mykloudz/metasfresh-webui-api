package de.metas.ui.web.handlingunits.process;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_M_AttributeInstance;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.handlingunits.HuId;
<<<<<<< HEAD
import de.metas.handlingunits.model.I_M_ShipmentSchedule;
import de.metas.handlingunits.picking.PickingCandidate;
import de.metas.handlingunits.picking.PickingCandidatePickStatus;
=======
import de.metas.handlingunits.picking.PickFrom;
>>>>>>> upstream/release
import de.metas.handlingunits.picking.PickingCandidateService;
import de.metas.handlingunits.picking.requests.PickRequest;
import de.metas.inoutcandidate.api.ShipmentScheduleId;
import de.metas.logging.LogManager;
import de.metas.order.OrderLineId;
import de.metas.picking.api.PickingSlotId;
import de.metas.process.IProcessDefaultParameter;
import de.metas.process.IProcessDefaultParametersProvider;
import de.metas.process.IProcessPrecondition;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.ui.web.handlingunits.HUEditorRow;
import de.metas.ui.web.picking.husToPick.HUsToPickViewFactory;
import de.metas.ui.web.pporder.PPOrderLineRow;
import de.metas.ui.web.pporder.PPOrderLinesView;
import de.metas.ui.web.process.adprocess.ViewBasedProcessTemplate;
import de.metas.ui.web.process.descriptor.ProcessParamLookupValuesProvider;
import de.metas.ui.web.view.IView;
import de.metas.ui.web.view.IViewRow;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementFieldDescriptor.LookupSource;
import de.metas.ui.web.window.model.lookup.LookupDataSourceContext;
import de.metas.util.GuavaCollectors;
import de.metas.util.Services;
import lombok.Builder;
import lombok.Value;

import static org.adempiere.model.InterfaceWrapperHelper.load;

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

public class WEBUI_M_HU_Pick extends ViewBasedProcessTemplate implements IProcessPrecondition, IProcessDefaultParametersProvider
{
	private static final Logger logger = LogManager.getLogger(WEBUI_M_HU_Pick.class);

	@Autowired
	private PickingCandidateService pickingCandidateService;

	@Param(parameterName = WEBUI_M_HU_Pick_ParametersFiller.PARAM_M_PickingSlot_ID, mandatory = true)
	private int pickingSlotIdInt;

	@Param(parameterName = WEBUI_M_HU_Pick_ParametersFiller.PARAM_M_ShipmentSchedule_ID, mandatory = true)
	private int shipmentScheduleIdInt;

	@Override
	protected ProcessPreconditionsResolution checkPreconditionsApplicable()
	{
		if (HUsToPickViewFactory.WINDOW_ID.equals(getWindowId()))
		{
			return ProcessPreconditionsResolution.rejectWithInternalReason("not needed in HUsToPick view");
		}

		final ImmutableList<HURow> firstRows = streamHURows().limit(2).collect(ImmutableList.toImmutableList());
		if (firstRows.isEmpty())
		{
			// NOTE: we decided to hide this action when there is not available,
			// because we want to cover the requirements of https://github.com/metasfresh/metasfresh-webui-api/issues/683,
			// were we need to hide the action for source HU lines... and does not worth the effort to handle particularly that case.
			return ProcessPreconditionsResolution.rejectWithInternalReason("no eligible HU rows found");
			// return ProcessPreconditionsResolution.reject(msgBL.getTranslatableMsgText(WEBUI_M_HU_Messages.MSG_WEBUI_ONLY_TOP_LEVEL_HU));
		}

		if (firstRows.size() != 1)
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		return ProcessPreconditionsResolution.accept();
	}

	private Stream<HURow> streamHURows()
	{
		return streamSelectedRows()
				.map(row -> toHURowOrNull(row))
				.filter(Predicates.notNull())
				.filter(HURow::isTopLevelHU)
				.filter(HURow::isHuStatusActive);
	}

	private HURow getSingleHURow()
	{
		return streamHURows()
				.collect(GuavaCollectors.singleElementOrThrow(() -> new AdempiereException("only one selected row was expected")));
	}

	@Override
	public Object getParameterDefaultValue(final IProcessDefaultParameter parameter)
	{
		return createNewDefaultParametersFiller().getDefaultValue(parameter);
	}

	@ProcessParamLookupValuesProvider(//
			parameterName = WEBUI_M_HU_Pick_ParametersFiller.PARAM_M_ShipmentSchedule_ID, //
			numericKey = true, //
			lookupSource = LookupSource.lookup)
	private LookupValuesList getShipmentScheduleValues(final LookupDataSourceContext context)
	{

		final HUEditorRow huEditorRow = getSingleHUEditorRow();

		// Get all the matching shipment schedules and then filter for schedules which are not picked and match the order-line attributes.
		return createNewDefaultParametersFiller()
				.getShipmentScheduleValues(context)
				.stream()
				.filter(shipmentSchedule -> (!isShipmentSchedulePicked(shipmentSchedule.getIdAsInt())
						&& doesHUAttribsMatchOrderLineAttribs(shipmentSchedule.getIdAsInt(), huEditorRow)))
				.collect(LookupValuesList.collect());

	}
	
	private HUEditorRow getSingleHUEditorRow() {
		return streamSelectedRows()
				.map(row -> HUEditorRow.cast(row))
				.collect(GuavaCollectors.singleElementOrThrow(() -> new AdempiereException("only one selected row was expected")));
	}

	// if picking candidate is available for the shipment schedule, then check if it is already picked
	private boolean isShipmentSchedulePicked(int shipmentScheduleId)
	{
		final Set<ShipmentScheduleId> shipmentScheduleIds = new HashSet<>();
		shipmentScheduleIds.add(ShipmentScheduleId.ofRepoId(shipmentScheduleId));

		final List<PickingCandidate> pickingCandidates = pickingCandidateService.getPickingCandidatesForShipmentSchedules(shipmentScheduleIds);
		if(pickingCandidates.isEmpty()) {
			return false;
		}

		PickingCandidate pickingCandidate = pickingCandidates.get(0);

		return (pickingCandidate.getShipmentScheduleId()
				.equals(ShipmentScheduleId.ofRepoId(shipmentScheduleId))
				&& pickingCandidate.getPickStatus().equals(PickingCandidatePickStatus.PICKED));
	}

	// For the order-line item corresponding to given shipment schedule, check if its attributes match to the corresponding attributes in the selected HU item.
	private boolean doesHUAttribsMatchOrderLineAttribs(int shipmentScheduleId, HUEditorRow huEditorRow)
	{

		final I_M_ShipmentSchedule shipmentSchedule = load(ShipmentScheduleId.ofRepoId(shipmentScheduleId), I_M_ShipmentSchedule.class);

		final IQueryBL queryBL = Services.get(IQueryBL.class);
		Set<I_M_AttributeInstance> attributeInstances = queryBL.createQueryBuilder(I_M_AttributeInstance.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_M_AttributeInstance.COLUMN_M_AttributeSetInstance_ID, shipmentSchedule.getM_AttributeSetInstance_ID())
				.create()
				.stream()
				.collect(ImmutableSet.toImmutableSet());

		return attributeInstances.stream()
				.allMatch(attributeInstance -> doesHUAttribsMatch(attributeInstance, huEditorRow));

	}

	// For the given attribute, match the corresponding attributes in the given HU item.
	private boolean doesHUAttribsMatch(I_M_AttributeInstance attributeInstance, HUEditorRow huEditorRow)
	{
		if (attributeInstance.getM_Attribute().isStorageRelevant())
		{
			final Object attribValue = huEditorRow.getAttributes()
					.getValue(attributeInstance.getM_Attribute().getValue());

			if(attribValue != null) {
				if (attribValue instanceof BigDecimal)
				{
					return ((BigDecimal)attribValue).compareTo(new BigDecimal(attributeInstance.getValue())) == 0;
				}
				else
				{
					return attribValue.equals(attributeInstance.getValue());
				}
			}
			return false;
		}
		return true;
	}
	

	private WEBUI_M_HU_Pick_ParametersFiller createNewDefaultParametersFiller()
	{
		final HURow row = getSingleHURow();
		return WEBUI_M_HU_Pick_ParametersFiller.defaultFillerBuilder()
				.huId(row.getHuId())
				.salesOrderLineId(getSalesOrderLineId())
				.build();
	}

	@ProcessParamLookupValuesProvider(//
			parameterName = WEBUI_M_HU_Pick_ParametersFiller.PARAM_M_PickingSlot_ID, //
			dependsOn = WEBUI_M_HU_Pick_ParametersFiller.PARAM_M_ShipmentSchedule_ID, //
			numericKey = true, //
			lookupSource = LookupSource.lookup)
	private LookupValuesList getPickingSlotValues(final LookupDataSourceContext context)
	{
		final WEBUI_M_HU_Pick_ParametersFiller filler = WEBUI_M_HU_Pick_ParametersFiller
				.pickingSlotFillerBuilder()
				.shipmentScheduleId(ShipmentScheduleId.ofRepoId(shipmentScheduleIdInt))
				.build();

		return filler.getPickingSlotValues(context);
	}

	private OrderLineId getSalesOrderLineId()
	{
		final IView view = getView();
		if (view instanceof PPOrderLinesView)
		{
			final PPOrderLinesView ppOrderLinesView = PPOrderLinesView.cast(view);
			return ppOrderLinesView.getSalesOrderLineId();
		}
		else
		{
			return null;
		}
	}

	@Override
	protected String doIt() throws Exception
	{
		final HURow row = getSingleHURow();
		pickHU(row);

		return MSG_OK;
	}

	private void pickHU(final HURow row)
	{
		final HuId huId = row.getHuId();
		final PickingSlotId pickingSlotId = PickingSlotId.ofRepoId(pickingSlotIdInt);
		final ShipmentScheduleId shipmentScheduleId = ShipmentScheduleId.ofRepoId(shipmentScheduleIdInt);
		pickingCandidateService.pickHU(PickRequest.builder()
				.shipmentScheduleId(shipmentScheduleId)
				.pickFrom(PickFrom.ofHuId(huId))
				.pickingSlotId(pickingSlotId)
				.build());
		// NOTE: we are not moving the HU to shipment schedule's locator.

		pickingCandidateService.processForHUIds(ImmutableSet.of(huId), shipmentScheduleId);
	}

	@Override
	protected void postProcess(final boolean success)
	{
		if (!success)
		{
			return;
		}

		invalidateView();
	}

	private static final HURow toHURowOrNull(final IViewRow row)
	{
		if (row instanceof HUEditorRow)
		{
			final HUEditorRow huRow = HUEditorRow.cast(row);
			return HURow.builder()
					.huId(huRow.getHuId())
					.topLevelHU(huRow.isTopLevel())
					.huStatusActive(huRow.isHUStatusActive())
					.build();
		}
		else if (row instanceof PPOrderLineRow)
		{
			final PPOrderLineRow ppOrderLineRow = PPOrderLineRow.cast(row);

			// this process does not apply to source HUs
			if (ppOrderLineRow.isSourceHU())
			{
				return null;
			}

			if (!ppOrderLineRow.getType().isHUOrHUStorage())
			{
				return null;
			}
			return HURow.builder()
					.huId(ppOrderLineRow.getHuId())
					.topLevelHU(ppOrderLineRow.isTopLevelHU())
					.huStatusActive(ppOrderLineRow.isHUStatusActive())
					.build();
		}
		else
		{
			new AdempiereException("Row type not supported: " + row).throwIfDeveloperModeOrLogWarningElse(logger);
			return null;
		}
	}

	@Value
	@Builder
	private static final class HURow
	{
		private final HuId huId;
		private final boolean topLevelHU;
		private final boolean huStatusActive;
	}
}
