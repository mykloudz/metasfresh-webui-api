package de.metas.ui.web.handlingunits;

import static org.adempiere.model.InterfaceWrapperHelper.loadOutOfTrx;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.ad.service.IADReferenceDAO;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.DBException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.model.PlainContextAware;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Attribute;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.bpartner.BPartnerId;
import de.metas.handlingunits.HuId;
import de.metas.handlingunits.IHUQueryBuilder;
import de.metas.handlingunits.IHUStatusBL;
import de.metas.handlingunits.IHandlingUnitsBL;
import de.metas.handlingunits.IHandlingUnitsDAO;
import de.metas.handlingunits.attribute.IAttributeValue;
import de.metas.handlingunits.attribute.storage.IAttributeStorage;
import de.metas.handlingunits.exceptions.HUException;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_Locator;
import de.metas.handlingunits.model.I_M_Warehouse;
import de.metas.handlingunits.model.X_M_HU;
import de.metas.handlingunits.model.X_M_HU_PI_Version;
import de.metas.handlingunits.reservation.HUReservationService;
import de.metas.handlingunits.storage.IHUProductStorage;
import de.metas.handlingunits.storage.IHUStorage;
import de.metas.handlingunits.storage.IHUStorageFactory;
import de.metas.i18n.IMsgBL;
import de.metas.logging.LogManager;
import de.metas.order.OrderLineId;
import de.metas.product.IProductBL;
import de.metas.product.ProductId;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.sql.SqlDocumentFilterConverter;
import de.metas.ui.web.document.filter.sql.SqlDocumentFilterConverterContext;
import de.metas.ui.web.document.filter.sql.SqlDocumentFilterConverters;
import de.metas.ui.web.handlingunits.HUIdsFilterHelper.HUIdsFilterData;
import de.metas.ui.web.handlingunits.util.HUPackingInfoFormatter;
import de.metas.ui.web.handlingunits.util.HUPackingInfos;
import de.metas.ui.web.view.SqlViewRowIdsOrderedSelectionFactory;
import de.metas.ui.web.view.ViewEvaluationCtx;
import de.metas.ui.web.view.ViewId;
import de.metas.ui.web.view.ViewRowIdsOrderedSelection;
import de.metas.ui.web.view.ViewRowIdsOrderedSelectionFactory;
import de.metas.ui.web.view.descriptor.SqlAndParams;
import de.metas.ui.web.view.descriptor.SqlViewBinding;
import de.metas.ui.web.view.descriptor.SqlViewKeyColumnNamesMap;
import de.metas.ui.web.view.descriptor.SqlViewRowIdsConverter;
import de.metas.ui.web.view.descriptor.SqlViewSelectData;
import de.metas.ui.web.view.descriptor.SqlViewSelectionQueryBuilder;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentIdsSelection;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;
import de.metas.ui.web.window.model.DocumentQueryOrderBy;
import de.metas.ui.web.window.model.sql.SqlOptions;
import de.metas.util.Check;
import de.metas.util.GuavaCollectors;
import de.metas.util.Services;
import de.metas.util.collections.PagedIterator.Page;
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

public class SqlHUEditorViewRepository implements HUEditorViewRepository
{
	private static final String MSG_HU_RESERVED = "de.metas.handlingunit.HU_Reserved";

	private static final transient Logger logger = LogManager.getLogger(SqlHUEditorViewRepository.class);

	private final WindowId windowId;

	private final HUEditorRowAttributesProvider attributesProvider;
	private final HUEditorRowIsProcessedPredicate rowProcessedPredicate;
	private final HUReservationService huReservationService;

	private final boolean showBestBeforeDate;

	private final SqlViewBinding sqlViewBinding;
	private final ViewRowIdsOrderedSelectionFactory viewSelectionFactory;
	private final SqlViewSelectData sqlViewSelect;

	@Builder
	private SqlHUEditorViewRepository(
			@NonNull final WindowId windowId,
			@NonNull final SqlViewBinding sqlViewBinding,
			@Nullable final HUEditorRowAttributesProvider attributesProvider,
			@Nullable final HUEditorRowIsProcessedPredicate rowProcessedPredicate,
			@NonNull final HUReservationService huReservationService,
			final boolean showBestBeforeDate)
	{
		this.windowId = windowId;

		this.attributesProvider = attributesProvider;
		this.rowProcessedPredicate = rowProcessedPredicate != null ? rowProcessedPredicate : HUEditorRowIsProcessedPredicates.NEVER;
		this.showBestBeforeDate = showBestBeforeDate;

		this.sqlViewBinding = sqlViewBinding;
		viewSelectionFactory = SqlViewRowIdsOrderedSelectionFactory.of(sqlViewBinding);
		sqlViewSelect = sqlViewBinding.getSqlViewSelect();

		this.huReservationService = huReservationService;
	}

	@Override
	public void invalidateCache()
	{
		if (attributesProvider != null)
		{
			attributesProvider.invalidateAll();
		}
	}

	@Override
	public SqlViewRowIdsConverter getRowIdsConverter()
	{
		return sqlViewBinding.getRowIdsConverter();
	}

	@Override
	public List<HUEditorRow> retrieveHUEditorRows(@NonNull final Set<HuId> huIds, @NonNull final HUEditorRowFilter filter)
	{
		huReservationService.warmup(huIds);

		final HuId topLevelHUId = null;
		return retrieveTopLevelHUs(huIds, filter)
				.stream()
				.map(hu -> createHUEditorRow(hu, topLevelHUId))
				.collect(GuavaCollectors.toImmutableList());
	}

	@Override
	public HUEditorRow retrieveForHUId(@Nullable final HuId huId)
	{
		if (huId == null)
		{
			return null;
		}

		// TODO: check if the huId is part of our collection

		final I_M_HU hu = Services.get(IHandlingUnitsDAO.class).getByIdOutOfTrx(huId);
		final HuId topLevelHUId = null; // assume given huId is a top level HU
		return createHUEditorRow(hu, topLevelHUId);
	}

	private static List<I_M_HU> retrieveTopLevelHUs(@NonNull final Collection<HuId> huIds, @NonNull final HUEditorRowFilter filter)
	{
		if (huIds.isEmpty())
		{
			return ImmutableList.of();
		}

		final IQueryBuilder<I_M_HU> queryBuilder = HUEditorRowFilters.toHUQueryBuilderPart(filter)
				.setContext(PlainContextAware.newOutOfTrx())
				.setOnlyTopLevelHUs()
				.setOnlyActiveHUs(false) // retrieve ALL HUs, see https://github.com/metasfresh/metasfresh-webui-api/issues/563
				.createQueryBuilder();

		if (huIds != null && !huIds.isEmpty())
		{
			queryBuilder.addInArrayFilter(I_M_HU.COLUMN_M_HU_ID, huIds);
		}

		return queryBuilder
				.create()
				.list();
	}

	private HUEditorRow createHUEditorRow(
			@NonNull final I_M_HU hu,
			final HuId topLevelHUId)
	{
		// final Stopwatch stopwatch = Stopwatch.createStarted();

		final IHandlingUnitsBL handlingUnitsBL = Services.get(IHandlingUnitsBL.class);
		final boolean aggregatedTU = handlingUnitsBL.isAggregateHU(hu);
		final String huUnitTypeCode = handlingUnitsBL.getHU_UnitType(hu);
		final HUEditorRowType huRecordType;
		if (aggregatedTU)
		{
			huRecordType = HUEditorRowType.TU;
		}
		else
		{
			huRecordType = HUEditorRowType.ofHU_UnitType(huUnitTypeCode);
		}
		final Optional<OrderLineId> orderLineIdWithReservation = huReservationService.getOrderLineIdByReservedVhuId(HuId.ofRepoId(hu.getM_HU_ID()));

		final String huUnitTypeDisplayName = huRecordType.getName();
		final JSONLookupValue huUnitTypeLookupValue = JSONLookupValue.of(huUnitTypeCode, huUnitTypeDisplayName);

		final JSONLookupValue huStatusDisplay = createHUStatusDisplayLookupValue(hu);
		final boolean processed = rowProcessedPredicate.isProcessed(hu);
		final HuId huId = HuId.ofRepoId(hu.getM_HU_ID());
		final HUEditorRowId rowId = HUEditorRowId.ofHU(huId, topLevelHUId);

		final HUEditorRow.Builder huEditorRow = HUEditorRow.builder(windowId)
				.setRowId(rowId)
				.setType(huRecordType)
				.setTopLevel(topLevelHUId == null)
				.setProcessed(processed)
				.setBPartnerId(BPartnerId.ofRepoIdOrNull(hu.getC_BPartner_ID()))
				.setAttributesProvider(attributesProvider)
				//
				.setCode(hu.getValue())
				.setHUUnitType(huUnitTypeLookupValue)
				.setHUStatusDisplay(huStatusDisplay)
				.setHUStatus(hu.getHUStatus())
				.setReservedForOrderLine(orderLineIdWithReservation.orElse(null))

				.setPackingInfo(extractPackingInfo(hu, huRecordType));

		//
		// Acquire Best Before Date if required
		if (showBestBeforeDate)
		{
			huEditorRow.setBestBeforeDate(extractBestBeforeDate(attributesProvider, rowId));
		}

		//
		// Locator

		huEditorRow.setLocator(createLocatorLookupValue(hu.getM_Locator_ID()));

		//
		// Product/UOM/Qty if there is only one product stored
		final IHUProductStorage singleProductStorage = getSingleProductStorage(hu);
		if (singleProductStorage != null)
		{
			huEditorRow
					.setProduct(createProductLookupValue(singleProductStorage.getProductId()))
					.setUOM(createUOMLookupValue(singleProductStorage.getC_UOM()))
					.setQtyCU(singleProductStorage.getQty().toBigDecimal());
		}

		//
		// Included HUs
		final HuId topLevelHUIdEffective = topLevelHUId != null ? topLevelHUId : huId;
		if (aggregatedTU)
		{
			final IHUStorageFactory storageFactory = handlingUnitsBL.getStorageFactory();
			storageFactory
					.getStorage(hu)
					.getProductStorages()
					.stream()
					.map(huStorage -> createHUEditorRow(huId, topLevelHUIdEffective, huStorage, processed))
					.forEach(huEditorRow::addIncludedRow);

		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_LoadLogistiqueUnit.equals(huUnitTypeCode))
		{
			final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
			handlingUnitsDAO.retrieveIncludedHUs(hu)
					.stream()
					.map(includedHU -> createHUEditorRow(includedHU, topLevelHUIdEffective))
					.forEach(huEditorRow::addIncludedRow);
		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_TransportUnit.equals(huUnitTypeCode))
		{
			final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
			final IHUStorageFactory storageFactory = handlingUnitsBL.getStorageFactory();
			handlingUnitsDAO.retrieveIncludedHUs(hu)
					.stream()
					.map(includedVHU -> storageFactory.getStorage(includedVHU))
					.flatMap(vhuStorage -> vhuStorage.getProductStorages().stream())
					.map(vhuProductStorage -> createHUEditorRow(huId, topLevelHUIdEffective, vhuProductStorage, processed))
					.forEach(huEditorRow::addIncludedRow);
		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_VirtualPI.equals(huUnitTypeCode))
		{
			// do nothing
		}
		else
		{
			throw new HUException("Unknown HU_UnitType=" + huUnitTypeCode + " for " + hu);
		}

		final HUEditorRow huEditorRowBuilt = huEditorRow.build();
		
		//*****************************************************************************************************
			String bhg_attribute = "";
			try {
				HUEditorRowAttributes rows = huEditorRowBuilt.getAttributes();
				Field field = HUEditorRowAttributes.class.getDeclaredField("attributesStorage");
				field.setAccessible(true);
				IAttributeStorage attributesStorage = (IAttributeStorage) field.get(rows);
				List<IAttributeValue> extendedClass = attributesStorage.getAttributeValues();
				for(IAttributeValue val : extendedClass){
					I_M_Attribute m_attribute = val.getM_Attribute();
					if(m_attribute.getName().equalsIgnoreCase("Width")){
						bhg_attribute = val.getValue().toString();
						bhg_attribute = bhg_attribute != "0" ? String.valueOf(bhg_attribute).split("\\.")[0] : "0";
					}
					if(m_attribute.getName().equalsIgnoreCase("Height")){
						String bhg_attribute1 = val.getValue().toString();
						bhg_attribute1 = bhg_attribute1 != "0" ? String.valueOf(bhg_attribute1).split("\\.")[0] : "0";
						bhg_attribute = bhg_attribute.concat("_").concat(bhg_attribute1);
						
					}
					if(m_attribute.getName().equalsIgnoreCase("Nos/Sets")){
						String bhg_attribute1 = val.getValue().toString();
						bhg_attribute1 = bhg_attribute1 != "0" ? String.valueOf(bhg_attribute1).split("\\.")[0] : "0";
						bhg_attribute = bhg_attribute.concat("_").concat(bhg_attribute1);
					}
				}
				
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException |IllegalAccessException e) {
				e.printStackTrace();
				e.getCause();
			}
			
		//*****************************************************************************************************

		// stopwatch.stop();
		// System.out.println("createHUEditorRow: created " + huEditorRowBuilt + " in " + stopwatch);
			final String attribute = (bhg_attribute.equals("0_0_0")) || (bhg_attribute.equals("")) ? "" : bhg_attribute;
			final HUEditorRow.Builder huEditorRow1 = HUEditorRow.builder(windowId)
					.setRowId(rowId)
					.setType(huRecordType)
					.setTopLevel(topLevelHUId == null)
					.setProcessed(processed)
					.setBPartnerId(BPartnerId.ofRepoIdOrNull(hu.getC_BPartner_ID()))
					.setAttributesProvider(attributesProvider)
					//
					.setCode(hu.getValue())
					.setHUUnitType(huUnitTypeLookupValue)
					.setHUStatusDisplay(huStatusDisplay)
					.setHUStatus(hu.getHUStatus())
					.setReservedForOrderLine(orderLineIdWithReservation.orElse(null))
					//.setBHG_Attributes(attribute)
					//.setPackingInfo(extractPackingInfo(hu, huRecordType));
					.setPackingInfo(attribute);

			//
			// Acquire Best Before Date if required
			if (showBestBeforeDate)
			{
				huEditorRow1.setBestBeforeDate(extractBestBeforeDate(attributesProvider, rowId));
			}

			//
			// Locator

			huEditorRow1.setLocator(createLocatorLookupValue(hu.getM_Locator_ID()));

			//
			// Product/UOM/Qty if there is only one product stored
			final IHUProductStorage singleProductStorage1 = getSingleProductStorage(hu);
			if (singleProductStorage1 != null)
			{
				huEditorRow1
						.setProduct(createProductLookupValue(singleProductStorage1.getProductId()))
						.setUOM(createUOMLookupValue(singleProductStorage1.getC_UOM()))
						.setQtyCU(singleProductStorage1.getQty().getAsBigDecimal());
			}

			//
			// Included HUs
			final HuId topLevelHUIdEffective1 = topLevelHUId != null ? topLevelHUId : huId;
			if (aggregatedTU)
			{
				final IHUStorageFactory storageFactory = handlingUnitsBL.getStorageFactory();
				storageFactory
						.getStorage(hu)
						.getProductStorages()
						.stream()
						.map(huStorage -> createHUEditorRow(huId, topLevelHUIdEffective1, huStorage, processed))
						.forEach(huEditorRow1::addIncludedRow);

			}
			else if (X_M_HU_PI_Version.HU_UNITTYPE_LoadLogistiqueUnit.equals(huUnitTypeCode))
			{
				final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
				handlingUnitsDAO.retrieveIncludedHUs(hu)
						.stream()
						.map(includedHU -> createHUEditorRow(includedHU, topLevelHUIdEffective1))
						.forEach(huEditorRow1::addIncludedRow);
			}
			else if (X_M_HU_PI_Version.HU_UNITTYPE_TransportUnit.equals(huUnitTypeCode))
			{
				final IHandlingUnitsDAO handlingUnitsDAO = Services.get(IHandlingUnitsDAO.class);
				final IHUStorageFactory storageFactory = handlingUnitsBL.getStorageFactory();
				handlingUnitsDAO.retrieveIncludedHUs(hu)
						.stream()
						.map(includedVHU -> storageFactory.getStorage(includedVHU))
						.flatMap(vhuStorage -> vhuStorage.getProductStorages().stream())
						.map(vhuProductStorage -> createHUEditorRow(huId, topLevelHUIdEffective1, vhuProductStorage, processed))
						.forEach(huEditorRow1::addIncludedRow);
			}
			else if (X_M_HU_PI_Version.HU_UNITTYPE_VirtualPI.equals(huUnitTypeCode))
			{
				// do nothing
			}
			else
			{
				throw new HUException("Unknown HU_UnitType=" + huUnitTypeCode + " for " + hu);
			}

			final HUEditorRow huEditorRowBuilt1 = huEditorRow1.build();

		return huEditorRowBuilt1;
	}

	private static final String extractPackingInfo(final I_M_HU hu, final HUEditorRowType huUnitType)
	{
		if (!huUnitType.isPureHU())
		{
			return "";
		}
		if (huUnitType == HUEditorRowType.VHU)
		{
			return "";
		}

		try
		{
			return HUPackingInfoFormatter.newInstance()
					.setShowLU(true)
					.format(HUPackingInfos.of(hu));
		}
		catch (final Exception ex)
		{
			logger.warn("Failed extracting packing info for {}", hu, ex);
			return "?";
		}
	}

	private IHUProductStorage getSingleProductStorage(final I_M_HU hu)
	{
		final IHUStorage huStorage = Services.get(IHandlingUnitsBL.class).getStorageFactory()
				.getStorage(hu);

		final ProductId productId = huStorage.getSingleProductIdOrNull();
		if (productId == null)
		{
			return null;
		}

		final IHUProductStorage productStorage = huStorage.getProductStorage(productId);
		return productStorage;
	}

	private HUEditorRow createHUEditorRow(
			final HuId parentHUId,
			final HuId topLevelHUId,
			@NonNull final IHUProductStorage huStorage,
			final boolean processed)
	{
		// final Stopwatch stopwatch = Stopwatch.createStarted();

		final I_M_HU hu = huStorage.getM_HU();
		final HuId huId = HuId.ofRepoId(hu.getM_HU_ID());
		final ProductId productId = huStorage.getProductId();
		final HUEditorRowAttributesProvider attributesProviderEffective = !huId.equals(parentHUId) ? attributesProvider : null;

		final Optional<OrderLineId> reservedForOrderLineId = huReservationService.getOrderLineIdByReservedVhuId(huId);

		final HUEditorRow huEditorRow = HUEditorRow.builder(windowId)
				.setRowId(HUEditorRowId.ofHUStorage(huId, topLevelHUId, productId))
				.setType(HUEditorRowType.HUStorage)
				.setTopLevel(false)
				.setProcessed(processed)
				.setAttributesProvider(attributesProviderEffective)
				//
				// .setHUId(huId)
				// .setCode(hu.getValue()) // NOTE: don't show value on storage level
				.setHUUnitType(JSONLookupValue.of(X_M_HU_PI_Version.HU_UNITTYPE_VirtualPI, "CU"))
				.setHUStatus(hu.getHUStatus())
				.setReservedForOrderLine(reservedForOrderLineId.orElse(null))
				.setHUStatusDisplay(createHUStatusDisplayLookupValue(hu))
				.setProduct(createProductLookupValue(productId))
				.setUOM(createUOMLookupValue(huStorage.getC_UOM()))
				.setQtyCU(huStorage.getQty().toBigDecimal())
				//
				.build();

		// System.out.println("createHUEditorRow: created " + huEditorRow + " (storage) in " + stopwatch);
		return huEditorRow;
	}

	public JSONLookupValue createProductLookupValue(@Nullable final ProductId productId)
	{
		if (productId == null)
		{
			return null;
		}

		final String displayName = Services.get(IProductBL.class).getProductValueAndName(productId);
		return JSONLookupValue.of(productId.getRepoId(), displayName);
	}

	private static JSONLookupValue createUOMLookupValue(@Nullable final I_C_UOM uom)
	{
		if (uom == null)
		{
			return null;
		}

		return JSONLookupValue.of(uom.getC_UOM_ID(), uom.getUOMSymbol());
	}

	private static JSONLookupValue createLocatorLookupValue(final int locatorId)
	{
		if (locatorId <= 0)
		{
			return null;
		}

		final I_M_Locator locator = loadOutOfTrx(locatorId, I_M_Locator.class);
		if (locator == null)
		{
			return JSONLookupValue.unknown(locatorId);
		}

		final I_M_Warehouse warehouse = loadOutOfTrx(locator.getM_Warehouse_ID(), I_M_Warehouse.class);

		final String caption = Stream.of(warehouse.getName(), locator.getValue(), locator.getX(), locator.getX1(), locator.getY(), locator.getZ())
				.filter(part -> !Check.isEmpty(part, true))
				.map(String::trim)
				.collect(Collectors.joining("_"));

		return JSONLookupValue.of(locatorId, caption);
	}

	private static JSONLookupValue createHUStatusDisplayLookupValue(@NonNull final I_M_HU hu)
	{
		final IHUStatusBL huStatusBL = Services.get(IHUStatusBL.class);

		final String huStatusKey;
		final String huStatusDisplayName;

		if (hu.isReserved() && huStatusBL.isPhysicalHU(hu)) // if e.g. a reserved HU was shipped, it shall be shown as "shipped" not "reserved"
		{
			huStatusKey = MSG_HU_RESERVED;
			huStatusDisplayName = Services.get(IMsgBL.class).getMsg(Env.getCtx(), huStatusKey);
		}
		else
		{
			final IADReferenceDAO adReferenceDAO = Services.get(IADReferenceDAO.class);
			huStatusKey = hu.getHUStatus();
			huStatusDisplayName = adReferenceDAO.retrieveListNameTrl(X_M_HU.HUSTATUS_AD_Reference_ID, huStatusKey);
		}

		return JSONLookupValue.of(huStatusKey, huStatusDisplayName);
	}

	private static LocalDate extractBestBeforeDate(final HUEditorRowAttributesProvider attributesProvider, final HUEditorRowId rowId)
	{
		if (attributesProvider == null)
		{
			return null;
		}

		final DocumentId attributesKey = attributesProvider.createAttributeKey(rowId.getHuId());
		final HUEditorRowAttributes attributes = attributesProvider.getAttributes(rowId.toDocumentId(), attributesKey);
		return attributes.getBestBeforeDate().orElse(null);
	}

	@Override
	public Set<HuId> retrieveHUIdsEffective(
			@NonNull final HUIdsFilterData huIdsFilter,
			@NonNull final List<DocumentFilter> filters,
			@NonNull final SqlDocumentFilterConverterContext context)
	{
		final ImmutableSet<HuId> onlyHUIds = extractHUIds(huIdsFilter);

		if (filters.isEmpty() && !huIdsFilter.hasInitialHUQuery() && onlyHUIds != null)
		{
			// shortcut: don't bother the DB but return the list of IDs that we already have
			return onlyHUIds;
		}

		// Create HU query
		IHUQueryBuilder huQuery = huIdsFilter.getInitialHUQueryOrNull();
		if (huQuery == null)
		{
			huQuery = Services.get(IHandlingUnitsDAO.class).createHUQueryBuilder();
		}
		huQuery.setContext(PlainContextAware.newOutOfTrx());

		// Only HUs
		if (onlyHUIds != null)
		{
			huQuery.addOnlyHUIds(HuId.toRepoIds(onlyHUIds));
		}

		// Exclude HUs
		huQuery.addHUIdsToExclude(HuId.toRepoIds(huIdsFilter.getShallNotHUIds()));

		//
		// Convert the "filters" to SQL
		if (!filters.isEmpty())
		{
			final SqlDocumentFilterConverter sqlFilterConverter = SqlDocumentFilterConverters.createEntityBindingEffectiveConverter(sqlViewBinding);
			huQuery.addFilter(sqlFilterConverter.createQueryFilter(filters,
					SqlOptions.usingTableAlias(sqlViewBinding.getTableAlias()),
					context));
		}

		final List<Integer> huRepoIds = huQuery.createQuery().listIds();
		return HuId.ofRepoIds(huRepoIds);
	}

	private static ImmutableSet<HuId> extractHUIds(@NonNull final HUIdsFilterData huIdsFilter)
	{
		final Set<HuId> initialHUIds = huIdsFilter.getInitialHUIds();
		final Set<HuId> huIdsToInclude = huIdsFilter.getMustHUIds();
		final Set<HuId> huIdsToExclude = huIdsFilter.getShallNotHUIds();

		if (initialHUIds == null && huIdsToInclude.isEmpty() && huIdsToExclude.isEmpty())
		{
			return null; // no restrictions
		}

		final Set<HuId> initialHUIdsOrEmpty = initialHUIds != null ? initialHUIds : ImmutableSet.of();

		return Stream.concat(initialHUIdsOrEmpty.stream(), huIdsToInclude.stream())
				.filter(huId -> !huIdsToExclude.contains(huId))
				.distinct()
				.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public Page<HuId> retrieveHUIdsPage(final ViewEvaluationCtx viewEvalCtx, final ViewRowIdsOrderedSelection selection, final int firstRow, final int maxRows)
	{
		final SqlAndParams sqlAndParams = sqlViewSelect.selectByPage()
				.viewEvalCtx(viewEvalCtx)
				.viewId(selection.getViewId())
				.firstRowZeroBased(firstRow)
				.pageLength(maxRows)
				.build();

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sqlAndParams.getSql(), ITrx.TRXNAME_ThreadInherited);
			pstmt.setMaxRows(maxRows);
			DB.setParameters(pstmt, sqlAndParams.getSqlParams());

			rs = pstmt.executeQuery();

			final Set<HuId> huIds = new LinkedHashSet<>();
			int lastRowMax = -1;
			while (rs.next())
			{
				final int huId = rs.getInt(I_M_HU.COLUMNNAME_M_HU_ID);
				if (huId <= 0)
				{
					continue;
				}
				huIds.add(HuId.ofRepoId(huId));

				final int lastRow = rs.getInt(SqlViewSelectData.COLUMNNAME_Paging_SeqNo_OneBased);
				lastRowMax = Math.max(lastRowMax, lastRow);
			}

			if (huIds.isEmpty())
			{
				// shall not happen
				return null;
			}
			else
			{
				final int lastRowZeroBased = lastRowMax - 1;
				return Page.ofRowsAndLastRowIndex(ImmutableList.copyOf(huIds), lastRowZeroBased);
			}
		}
		catch (final SQLException ex)
		{
			throw DBException.wrapIfNeeded(ex)
					.setSqlIfAbsent(sqlAndParams.getSql(), sqlAndParams.getSqlParams());
		}
		finally
		{
			DB.close(rs, pstmt);
		}

	}

	@Override
	public ViewRowIdsOrderedSelection createSelection(
			@NonNull final ViewEvaluationCtx viewEvalCtx,
			final ViewId viewId,
			final List<DocumentFilter> filters,
			final List<DocumentQueryOrderBy> orderBys,
			final SqlDocumentFilterConverterContext context)
	{
		final boolean applySecurityRestrictions = true;
		return viewSelectionFactory.createOrderedSelection(viewEvalCtx, viewId, filters, orderBys, applySecurityRestrictions, context);
	}

	@Override
	public ViewRowIdsOrderedSelection createSelectionFromSelection(
			@NonNull final ViewEvaluationCtx viewEvalCtx,
			final ViewRowIdsOrderedSelection fromSelection,
			final List<DocumentQueryOrderBy> orderBys)
	{
		return viewSelectionFactory.createOrderedSelectionFromSelection(viewEvalCtx, fromSelection, orderBys);
	}

	@Override
	public ViewRowIdsOrderedSelection addRowIdsToSelection(final ViewRowIdsOrderedSelection selection, final DocumentIdsSelection rowIdsToAdd)
	{
		return viewSelectionFactory.addRowIdsToSelection(selection, rowIdsToAdd);
	}

	@Override
	public ViewRowIdsOrderedSelection removeRowIdsFromSelection(final ViewRowIdsOrderedSelection selection, final DocumentIdsSelection rowIdsToRemove)
	{
		return viewSelectionFactory.removeRowIdsFromSelection(selection, rowIdsToRemove);
	}

	@Override
	public boolean containsAnyOfRowIds(final ViewRowIdsOrderedSelection selection, final DocumentIdsSelection rowIds)
	{
		return viewSelectionFactory.containsAnyOfRowIds(selection, rowIds);
	}

	@Override
	public void deleteSelection(final ViewRowIdsOrderedSelection selection)
	{
		viewSelectionFactory.deleteSelection(selection.getViewId());
	}

	@Override
	public String buildSqlWhereClause(final ViewRowIdsOrderedSelection selection, final DocumentIdsSelection rowIds)
	{
		return SqlViewSelectionQueryBuilder.prepareSqlWhereClause()
				.sqlTableAlias(I_M_HU.Table_Name)
				.keyColumnNamesMap(SqlViewKeyColumnNamesMap.ofIntKeyField(I_M_HU.COLUMNNAME_M_HU_ID))
				.selectionId(selection.getSelectionId())
				.rowIds(rowIds)
				.rowIdsConverter(getRowIdsConverter())
				.build();
	}

	@Override
	public void warmUp(@NonNull final Set<HuId> huIds)
	{
		InterfaceWrapperHelper.loadByRepoIdAwares(huIds, I_M_HU.class); // caches the given HUs with one SQL query
		huReservationService.warmup(huIds);
	}

}
