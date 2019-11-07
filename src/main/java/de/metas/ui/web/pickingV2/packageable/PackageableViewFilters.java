package de.metas.ui.web.pickingV2.packageable;

import java.util.List;

import org.adempiere.warehouse.WarehouseTypeId;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_M_Shipper;
import org.compiere.model.I_M_Warehouse_Type;

import de.metas.bpartner.BPartnerId;
import de.metas.edi.model.I_C_Order;
import de.metas.i18n.IMsgBL;
import de.metas.order.OrderId;
import de.metas.shipping.ShipperId;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilterDescriptor;
import de.metas.ui.web.document.filter.DocumentFilterParamDescriptor;
import de.metas.ui.web.document.filter.DocumentFilterParamDescriptor.Builder;
import de.metas.ui.web.document.filter.provider.DocumentFilterDescriptorsProvider;
import de.metas.ui.web.document.filter.provider.ImmutableDocumentFilterDescriptorsProvider;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.sql.SqlLookupDescriptor;
import de.metas.util.Services;
import lombok.experimental.UtilityClass;

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

@UtilityClass
final class PackageableViewFilters
{
	public static DocumentFilterDescriptorsProvider getDescriptors()
	{
		return ImmutableDocumentFilterDescriptorsProvider.of(createDefaultFilterDescriptor());
	}

	private static DocumentFilterDescriptor createDefaultFilterDescriptor()
	{
		final DocumentFilterParamDescriptor.Builder orderParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_C_Order_ID)
				.setWidgetType(DocumentFieldWidgetType.Lookup)
				.setLookupDescriptor(SqlLookupDescriptor.searchInTable(I_C_Order.Table_Name).provideForFilter());

		final DocumentFilterParamDescriptor.Builder customerParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_Customer_ID)
				.setWidgetType(DocumentFieldWidgetType.Lookup)
				.setLookupDescriptor(SqlLookupDescriptor.searchInTable(I_C_BPartner.Table_Name).provideForFilter());

		final DocumentFilterParamDescriptor.Builder warehouseTypeParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_M_Warehouse_Type_ID)
				.setWidgetType(DocumentFieldWidgetType.Lookup)
				.setLookupDescriptor(SqlLookupDescriptor.searchInTable(I_M_Warehouse_Type.Table_Name).provideForFilter());

		final DocumentFilterParamDescriptor.Builder deliveryDateParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_DeliveryDate)
				.setDisplayName(Services.get(IMsgBL.class).translatable(PackageableViewFilterVO.PARAM_DeliveryDate))
				.setWidgetType(DocumentFieldWidgetType.LocalDate);

		final DocumentFilterParamDescriptor.Builder preparationDateParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_PreparationDate)
				.setWidgetType(DocumentFieldWidgetType.LocalDate);

		final DocumentFilterParamDescriptor.Builder shipperParameter = newParamDescriptor(PackageableViewFilterVO.PARAM_M_Shipper_ID)
				.setWidgetType(DocumentFieldWidgetType.Lookup)
				.setLookupDescriptor(SqlLookupDescriptor.searchInTable(I_M_Shipper.Table_Name).provideForFilter());


		return DocumentFilterDescriptor.builder()
				.setFrequentUsed(true)
				.setFilterId(PackageableViewFilterVO.FILTER_ID)
				.setDisplayName(Services.get(IMsgBL.class).getTranslatableMsgText("Default"))
				.addParameter(orderParameter)
				.addParameter(customerParameter)
				.addParameter(warehouseTypeParameter)
				.addParameter(deliveryDateParameter)
				.addParameter(preparationDateParameter)
				.addParameter(shipperParameter)
				.build();
	}

	private static Builder newParamDescriptor(final String fieldName)
	{
		return DocumentFilterParamDescriptor.builder()
				.setFieldName(fieldName)
				.setDisplayName(Services.get(IMsgBL.class).translatable(fieldName));
	}

	public static PackageableViewFilterVO extractPackageableViewFilterVO(final List<DocumentFilter> filters)
	{
		return filters.stream()
				.filter(filter -> PackageableViewFilterVO.FILTER_ID.equals(filter.getFilterId()))
				.map(filter -> toPackageableViewFilterVO(filter))
				.findFirst()
				.orElse(PackageableViewFilterVO.ANY);
	}

	private static PackageableViewFilterVO toPackageableViewFilterVO(final DocumentFilter filter)
	{
		return PackageableViewFilterVO.builder()
				.salesOrderId(filter.getParameterValueAsRepoIdOrNull(PackageableViewFilterVO.PARAM_C_Order_ID, OrderId::ofRepoIdOrNull))
				.customerId(filter.getParameterValueAsRepoIdOrNull(PackageableViewFilterVO.PARAM_Customer_ID, BPartnerId::ofRepoIdOrNull))
				.warehouseTypeId(filter.getParameterValueAsRepoIdOrNull(PackageableViewFilterVO.PARAM_M_Warehouse_Type_ID, WarehouseTypeId::ofRepoIdOrNull))
				.deliveryDate(filter.getParameterValueAsLocalDateOrNull(PackageableViewFilterVO.PARAM_DeliveryDate))
				.preparationDate(filter.getParameterValueAsLocalDateOrNull(PackageableViewFilterVO.PARAM_PreparationDate))
				.shipperId(filter.getParameterValueAsRepoIdOrNull(PackageableViewFilterVO.PARAM_M_Shipper_ID, ShipperId::ofRepoIdOrNull))
				.build();
	}

}
