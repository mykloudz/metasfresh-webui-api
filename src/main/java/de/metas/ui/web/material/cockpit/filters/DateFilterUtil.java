package de.metas.ui.web.material.cockpit.filters;

import java.util.Collection;

import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.util.Env;

import de.metas.i18n.IMsgBL;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilterDescriptor;
import de.metas.ui.web.document.filter.DocumentFilterParam;
import de.metas.ui.web.document.filter.DocumentFilterParam.Operator;
import de.metas.ui.web.document.filter.DocumentFilterParamDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
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
class DateFilterUtil
{
	private static final String MSG_FILTER_CAPTION = "Date";

	public static DocumentFilterDescriptor createFilterDescriptor()
	{
		final DocumentFilterParamDescriptor.Builder standaloneParamDescriptor = DocumentFilterParamDescriptor.builder()
				.setFieldName(DateFilterVO.PARAM_Date)
				.setDisplayName(Services.get(IMsgBL.class).translatable(DateFilterVO.PARAM_Date))
				.setWidgetType(DocumentFieldWidgetType.Date)
				.setOperator(Operator.EQUAL)
				.setMandatory(true)
				.setShowIncrementDecrementButtons(true);

		return DocumentFilterDescriptor.builder()
				.setFrequentUsed(true)
				.setFilterId(DateFilterVO.FILTER_ID)
				.setDisplayName(Services.get(IMsgBL.class).getTranslatableMsgText(MSG_FILTER_CAPTION))
				.addParameter(standaloneParamDescriptor)
				.build();
	}

	public static DocumentFilter createFilterToday()
	{
		return DocumentFilter.builder()
				.setFilterId(DateFilterVO.FILTER_ID)
				.setCaption(Services.get(IMsgBL.class).translatable(DateFilterVO.PARAM_Date))
				.addParameter(DocumentFilterParam.ofNameOperatorValue(DateFilterVO.PARAM_Date, Operator.EQUAL, Env.getDate(Env.getCtx())))
				.build();
	}

	public DateFilterVO extractDateFilterVO(final Collection<DocumentFilter> filters)
	{
		return filters.stream()
				.filter(filter -> DateFilterVO.FILTER_ID.equals(filter.getFilterId()))
				.map(filter -> extractDateFilterVO(filter))
				.findFirst()
				.orElse(DateFilterVO.EMPTY);
	}

	public DateFilterVO extractDateFilterVO(final DocumentFilter filter)
	{
		Check.assume(DateFilterVO.FILTER_ID.equals(filter.getFilterId()), "Filter ID is {} but it was {}", DateFilterVO.FILTER_ID, filter);
		return DateFilterVO.builder()
				.date(filter.getParameterValueAsDate(DateFilterVO.PARAM_Date, null))
				.build();
	}

}
