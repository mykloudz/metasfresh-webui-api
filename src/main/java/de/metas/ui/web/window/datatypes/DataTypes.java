package de.metas.ui.web.window.datatypes;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DisplayType;
import org.compiere.util.TimeUtil;
import org.slf4j.Logger;

import de.metas.logging.LogManager;
import de.metas.ui.web.upload.WebuiImageId;
import de.metas.ui.web.window.datatypes.LookupValue.IntegerLookupValue;
import de.metas.ui.web.window.datatypes.LookupValue.StringLookupValue;
import de.metas.ui.web.window.datatypes.json.DateTimeConverters;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;
import de.metas.ui.web.window.datatypes.json.JSONLookupValuesList;
import de.metas.ui.web.window.datatypes.json.JSONRange;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.model.lookup.LookupValueByIdSupplier;
import de.metas.util.Check;
import de.metas.util.lang.RepoIdAware;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

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

@UtilityClass
public final class DataTypes
{
	private static final Logger logger = LogManager.getLogger(DataTypes.class);

	/**
	 * Checks if given values are both null or equal. This method works like {@link Objects#equals(Object)} with following exceptions:
	 * <ul>
	 * <li>{@link BigDecimal}s are compared excluding the scale (so "1.00" equals with "1.0")
	 * </ul>
	 *
	 * @param value1
	 * @param value2
	 * @return
	 */
	public static <T> boolean equals(final T value1, final T value2)
	{
		if (value1 == value2)
		{
			return true;
		}
		else if (value1 == null)
		{
			return false;
		}
		//
		// Special case: BigDecimals => we consider them equal if their value is equal, EXCLUDING the scale
		else if (value1 instanceof BigDecimal && value2 instanceof BigDecimal)
		{
			return ((BigDecimal)value1).compareTo((BigDecimal)value2) == 0;
		}
		else
		{
			return value1.equals(value2);
		}
	}

	/**
	 * Converts given value to target class.
	 *
	 * @param fieldName field name, needed only for logging purposes
	 * @param value value to be converted
	 * @param widgetType widget type (optional)
	 * @param targetType target type
	 * @param lookupDataSource optional Lookup data source, if needed
	 * @return converted value
	 */
	public static <T> T convertToValueClass(
			@NonNull final String fieldName,
			@Nullable final Object value,
			@Nullable final DocumentFieldWidgetType widgetType,
			@NonNull final Class<T> targetType,
			@Nullable final LookupValueByIdSupplier lookupDataSource)
	{
		if (value == null)
		{
			return null;
		}

		// Quick win: value has precisely the same class as targetType
		// NOTE: at this point don't accept if value is a subclass of target class because in most of the cases it's expected to return precisely the target type.
		// One example where this is critical is when the value is java.sql.Timestamp and the target type is java.util.Date.
		// If we would not return java.util.Date then all value changed comparing will fail.
		if (targetType.equals(value.getClass()))
		{
			return cast(value);
		}
		else if (Object.class == targetType)
		{
			return cast(value);
		}

		try
		{
			if (String.class == targetType)
			{
				return cast(convertToString(value));
			}
			else if (java.util.Date.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.ZonedDateTime;
				return cast(TimeUtil.asDate(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (Timestamp.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.ZonedDateTime;
				return cast(TimeUtil.asTimestamp(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (ZonedDateTime.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.ZonedDateTime;
				return cast(TimeUtil.asZonedDateTime(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (LocalDate.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.LocalDate;
				return cast(TimeUtil.asLocalDate(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (LocalTime.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.LocalTime;
				return cast(TimeUtil.asLocalTime(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (Instant.class == targetType)
			{
				final DocumentFieldWidgetType widgetTypeEffective = widgetType != null ? widgetType : DocumentFieldWidgetType.Timestamp;
				return cast(TimeUtil.asInstant(DateTimeConverters.fromObject(value, widgetTypeEffective)));
			}
			else if (Integer.class == targetType || int.class == targetType)
			{
				return cast(convertToInteger(value));
			}
			else if (BigDecimal.class == targetType)
			{
				return cast(convertToBigDecimal(value));
			}
			else if (Boolean.class == targetType || boolean.class == targetType)
			{
				return cast(convertToBoolean(value));
			}
			else if (IntegerLookupValue.class == targetType)
			{
				return cast(convertToIntegerLookupValue(value, lookupDataSource));
			}
			else if (StringLookupValue.class == targetType)
			{
				return cast(convertToStringLookupValue(value, lookupDataSource));
			}
			else if (LookupValuesList.class == targetType)
			{
				return cast(convertToLookupValuesList(value));
			}
			else if (DateRangeValue.class == targetType)
			{
				return cast(convertToDateRangeValue(value));
			}
			else if (Password.class == targetType)
			{
				return cast(Password.ofNullableString(value.toString()));
			}
			else if (ColorValue.class == targetType)
			{
				return cast(convertToColorValue(value));
			}
			else if (WebuiImageId.class == targetType)
			{
				return cast(WebuiImageId.ofNullableObject(value));
			}

			//
			// Fallbacks
			//

			// consider empty strings as null objects
			if (value instanceof String || value.toString().isEmpty())
			{
				return null;
			}
			else if (targetType.isInstance(value))
			{
				logger.warn("Possible optimization issue: target type is assignable from source type, but they are not the same class."
						+ "\n In future we will disallow this case, so please check and fix it."
						+ "\n Field name: " + fieldName
						+ "\n Target type: " + targetType
						+ "\n Source type: " + value.getClass()
						+ "\n Value: " + value
						+ "\n LookupDataSource: " + lookupDataSource
						+ "\n\n Suggestions:"
						+ "\n * if the call is coming from a proxy interface, try using right type (e.g. IntegerLookupValue instead of LookupValue).");
				return cast(value);
			}
			else
			{
				throw new ValueConversionException();
			}

		}
		catch (final Exception ex)
		{
			throw ValueConversionException.wrapIfNeeded(ex)
					.setFieldName(fieldName)
					.setFromValue(value)
					.setTargetType(targetType)
					.setWidgetType(widgetType)
					.setLookupDataSource(lookupDataSource);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(final Object value)
	{
		return (T)value;
	}

	private static String convertToString(final Object value)
	{
		if (value == null)
		{
			return null;
		}

		if (value instanceof String)
		{
			return (String)value;
		}
		else if (value instanceof Map)
		{
			// this is not allowed for consistency. let it fail.
			throw new AdempiereException("Converting Map to String is not allowed for consistency. Might be a development error");
		}
		else if (value instanceof LookupValue)
		{
			return ((LookupValue)value).getIdAsString();
		}
		// For any other case, blindly convert it to string
		else
		{
			return value.toString();
		}
	}

	private static Integer convertToInteger(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof Integer)
		{
			return (Integer)value;
		}
		else if (value instanceof String)
		{
			final String valueStr = (String)value;
			if (valueStr.isEmpty())
			{
				return null;
			}

			final BigDecimal valueBD = new BigDecimal(valueStr);
			return valueBD.intValueExact();
		}
		else if (value instanceof Number)
		{
			return ((Number)value).intValue();
		}
		else if (value instanceof LookupValue)
		{
			return ((LookupValue)value).getIdAsInt();
		}
		else if (value instanceof Map)
		{
			@SuppressWarnings("unchecked")
			final Map<String, Object> map = (Map<String, Object>)value;
			final IntegerLookupValue lookupValue = JSONLookupValue.integerLookupValueFromJsonMap(map);
			return lookupValue.getIdAsInt();
		}
		else if (value instanceof RepoIdAware)
		{
			return ((RepoIdAware)value).getRepoId();
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static BigDecimal convertToBigDecimal(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof BigDecimal)
		{
			return (BigDecimal)value;
		}
		if (value instanceof String)
		{
			final String valueStr = (String)value;
			return valueStr.isEmpty() ? null : new BigDecimal(valueStr);
		}
		else if (value instanceof Integer)
		{
			final int valueInt = (int)value;
			return BigDecimal.valueOf(valueInt);
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static boolean convertToBoolean(final Object value)
	{
		if (value == null)
		{
			return false;
		}
		else if (value instanceof Boolean)
		{
			return (boolean)value;
		}
		else
		{
			final Object valueToConv;
			if (value instanceof StringLookupValue)
			{
				// If String lookup value then consider only the Key.
				// usage example 1: the Posted column which can be Y, N and some other error codes.
				// In this case we want to convert the "Y" to "true".
				// usage example 2: some column which is List and the reference is "_YesNo".
				valueToConv = ((StringLookupValue)value).getIdAsString();
			}
			else
			{
				valueToConv = value;
			}

			return DisplayType.toBoolean(valueToConv, Boolean.FALSE);
		}
	}

	private static IntegerLookupValue convertToIntegerLookupValue(final Object value, final LookupValueByIdSupplier lookupDataSource)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof LookupValue)
		{
			final LookupValue lookupValue = (LookupValue)value;
			return toIntegerLookupValue(lookupValue);
		}
		else if (value instanceof Map)
		{
			@SuppressWarnings("unchecked")
			final Map<String, Object> map = (Map<String, Object>)value;
			final IntegerLookupValue lookupValue = JSONLookupValue.integerLookupValueFromJsonMap(map);

			if (Check.isEmpty(lookupValue.getDisplayName(), true) && lookupDataSource != null)
			{
				// corner case: the frontend sent a lookup value like '{ 1234567 : "" }'
				// => we need to resolve the name against the lookup
				// see https://github.com/metasfresh/metasfresh-webui/issues/230
				final LookupValue lookupValueResolved = lookupDataSource.findById(lookupValue.getId());
				return toIntegerLookupValue(lookupValueResolved);
			}
			else
			{
				return lookupValue;
			}
		}
		else if (value instanceof Number)
		{
			final int valueInt = ((Number)value).intValue();
			if (lookupDataSource != null)
			{
				final LookupValue lookupValue = lookupDataSource.findById(valueInt);
				// TODO: what if lookupValue was not found, i.e. is null?
				return toIntegerLookupValue(lookupValue);
			}
			else
			{
				throw new ValueConversionException();
			}
		}
		else if (value instanceof String)
		{
			final String valueStr = (String)value;
			if (valueStr.isEmpty())
			{
				return null;
			}

			if (lookupDataSource != null)
			{
				final LookupValue lookupValue = lookupDataSource.findById(valueStr);
				// TODO: what if lookupValue was not found, i.e. is null?
				return toIntegerLookupValue(lookupValue);
			}
			else
			{
				throw new ValueConversionException();
			}
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static IntegerLookupValue toIntegerLookupValue(final LookupValue lookupValue)
	{
		if (lookupValue == null)
		{
			return null;
		}
		else if (lookupValue instanceof IntegerLookupValue)
		{
			return (IntegerLookupValue)lookupValue;
		}
		else if (lookupValue instanceof StringLookupValue)
		{
			// TODO: implement https://github.com/metasfresh/metasfresh-webui-api/issues/417
			final StringLookupValue stringLookupValue = (StringLookupValue)lookupValue;
			return IntegerLookupValue.of(stringLookupValue);
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static StringLookupValue convertToStringLookupValue(final Object value, final LookupValueByIdSupplier lookupDataSource)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof LookupValue)
		{
			final LookupValue lookupValue = (LookupValue)value;
			return toStringLookupValue(lookupValue);
		}
		if (value instanceof Map)
		{
			@SuppressWarnings("unchecked")
			final Map<String, Object> map = (Map<String, Object>)value;
			final StringLookupValue lookupValue = JSONLookupValue.stringLookupValueFromJsonMap(map);

			if (Check.isEmpty(lookupValue.getDisplayName(), true) && lookupDataSource != null)
			{
				// corner case: the frontend sent a lookup value like '{ "someKey" : "" }'
				// => we need to resolve the name against the lookup
				// see https://github.com/metasfresh/metasfresh-webui/issues/230
				final LookupValue lookupValueResolved = lookupDataSource.findById(lookupValue.getId());
				return toStringLookupValue(lookupValueResolved);
			}
			else
			{
				return lookupValue;
			}
		}
		else if (value instanceof String)
		{
			final String valueStr = (String)value;
			if (valueStr.isEmpty())
			{
				return null;
			}

			if (lookupDataSource != null)
			{
				final LookupValue lookupValue = lookupDataSource.findById(valueStr);
				// TODO: what if lookupValue was not found, i.e. is null?
				return toStringLookupValue(lookupValue);
			}
			else
			{
				throw new ValueConversionException();
			}
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static StringLookupValue toStringLookupValue(final LookupValue lookupValue)
	{
		if (lookupValue == null)
		{
			return null;
		}
		else if (lookupValue instanceof StringLookupValue)
		{
			return (StringLookupValue)lookupValue;
		}
		else if (lookupValue instanceof IntegerLookupValue)
		{
			final IntegerLookupValue lookupValueInt = (IntegerLookupValue)lookupValue;
			return StringLookupValue.of(lookupValueInt.getIdAsString(), lookupValueInt.getDisplayName());
		}
		else
		{
			throw new AdempiereException("value type not supported");
		}
	}

	private static LookupValuesList convertToLookupValuesList(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof String && ((String)value).isEmpty())
		{
			return null;
		}
		else if (value instanceof LookupValuesList)
		{
			final LookupValuesList lookupValuesList = (LookupValuesList)value;
			return lookupValuesList;
		}
		else if (value instanceof Map)
		{
			@SuppressWarnings("unchecked")
			final Map<String, Object> map = (Map<String, Object>)value;
			final LookupValuesList lookupValuesList = JSONLookupValuesList.lookupValuesListFromJsonMap(map);
			if (lookupValuesList.isEmpty())
			{
				return null;
			}
			return lookupValuesList;
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static DateRangeValue convertToDateRangeValue(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof DateRangeValue)
		{
			return (DateRangeValue)value;
		}
		else if (value instanceof Map)
		{
			@SuppressWarnings("unchecked")
			final Map<String, String> map = (Map<String, String>)value;
			return JSONRange.dateRangeFromJSONMap(map);
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	private static ColorValue convertToColorValue(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		else if (value instanceof ColorValue)
		{
			return (ColorValue)value;
		}
		else if (value instanceof String)
		{
			return ColorValue.ofHexString(value.toString());
		}
		else
		{
			throw new ValueConversionException();
		}
	}

	@SuppressWarnings("serial")
	private static class ValueConversionException extends AdempiereException
	{
		public static ValueConversionException wrapIfNeeded(final Throwable ex)
		{
			if (ex instanceof ValueConversionException)
			{
				return (ValueConversionException)ex;
			}

			final Throwable cause = extractCause(ex);
			if (cause instanceof ValueConversionException)
			{
				return (ValueConversionException)cause;
			}
			else
			{
				return new ValueConversionException(cause);
			}
		}

		public ValueConversionException()
		{
			this("no conversion rule defined to convert the value to target type");
		}

		public ValueConversionException(final String message)
		{
			super(message);
			appendParametersToMessage();
		}

		public ValueConversionException(final Throwable cause)
		{
			super("Conversion failed because: " + cause.getLocalizedMessage(), cause);
			appendParametersToMessage();
		}

		public ValueConversionException setFieldName(final String fieldName)
		{
			setParameter("fieldName", fieldName);
			return this;
		}

		public ValueConversionException setWidgetType(final DocumentFieldWidgetType widgetType)
		{
			setParameter("widgetType", widgetType);
			return this;
		}

		public ValueConversionException setFromValue(final Object fromValue)
		{
			setParameter("value", fromValue);
			setParameter("valueClass", fromValue != null ? fromValue.getClass() : null);
			return this;
		}

		public ValueConversionException setTargetType(final Class<?> targetType)
		{
			setParameter("targetType", targetType);
			return this;
		}

		public ValueConversionException setLookupDataSource(final LookupValueByIdSupplier lookupDataSource)
		{
			setParameter("lookupDataSource", lookupDataSource);
			return this;
		}
	}
}
