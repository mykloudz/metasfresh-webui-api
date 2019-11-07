package de.metas.ui.web.window.descriptor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.adempiere.exceptions.AdempiereException;
import org.slf4j.Logger;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import de.metas.i18n.IMsgBL;
import de.metas.i18n.ITranslatableString;
import de.metas.i18n.TranslatableStrings;
import de.metas.logging.LogManager;
import de.metas.process.BarcodeScannerType;
import de.metas.ui.web.window.datatypes.MediaType;
import de.metas.ui.web.window.exceptions.DocumentLayoutBuildException;
import de.metas.util.Check;
import de.metas.util.GuavaCollectors;
import de.metas.util.Services;
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

public final class DocumentLayoutElementDescriptor
{
	public static Builder builder()
	{
		return new Builder();
	}

	public static Builder builder(final DocumentFieldDescriptor... fields)
	{
		Check.assumeNotEmpty(fields, "fields is not empty");

		final DocumentFieldDescriptor firstField = fields[0];

		final Builder elementBuilder = new Builder()
				.setCaption(firstField.getCaption())
				// .setDescription(firstField.getDescription())
				.setWidgetType(firstField.getWidgetType())
				.setWidgetSize(firstField.getWidgetSize());

		for (final DocumentFieldDescriptor field : fields)
		{
			elementBuilder.addField(DocumentLayoutElementFieldDescriptor.builder(field.getFieldName())
					.setPublicField(true)
					.setLookupInfos(field.getLookupDescriptor().orElse(null))
					.setSupportZoomInto(field.isSupportZoomInto()));
		}

		return elementBuilder;
	}

	public static Optional<Builder> builderOrEmpty(
			@NonNull final DocumentEntityDescriptor entityDescriptor,
			@NonNull final String... fieldNames)
	{
		Check.assumeNotEmpty(fieldNames, "fieldNames is not empty");

		final DocumentFieldDescriptor[] elementFields = Stream.of(fieldNames)
				.map(fieldName -> entityDescriptor.getFieldOrNull(fieldName))
				.filter(Predicates.notNull())
				.toArray(size -> new DocumentFieldDescriptor[size]);

		if (elementFields.length == 0)
		{
			return Optional.empty();
		}
		return Optional.of(builder(elementFields));
	}

	private final String internalName;
	private final boolean gridElement;

	private final ITranslatableString caption;
	private final ITranslatableString description;

	private final DocumentFieldWidgetType widgetType;
	private final boolean allowShowPassword; // in case widgetType is Password
	private final boolean multilineText; // in case widgetType is Text
	private final int multilineTextLines; // in case widgetType is Text
	private final ButtonFieldActionDescriptor buttonActionDescriptor;
	private final BarcodeScannerType barcodeScannerType;

	private final LayoutType layoutType;
	private final WidgetSize widgetSize;
	private final boolean advancedField;
	private final ImmutableSet<MediaType> restrictToMediaTypes;

	private final LayoutAlign gridAlign;
	private final ViewEditorRenderMode viewEditorRenderMode;
	private final boolean viewAllowSorting;

	private final Set<DocumentLayoutElementFieldDescriptor> fields;

	private String _captionAsFieldNames; // lazy

	private static final Joiner JOINER_FieldNames = Joiner.on(" | ").skipNulls();

	private DocumentLayoutElementDescriptor(final Builder builder)
	{
		internalName = builder.getInternalName();
		gridElement = builder.isGridElement();

		caption = builder.getCaption();
		description = builder.getDescription();

		widgetType = builder.getWidgetType();

		allowShowPassword = builder.isAllowShowPassword();
		multilineText = builder.isMultilineText();
		multilineTextLines = builder.getMultilineTextLines();
		buttonActionDescriptor = builder.getButtonActionDescriptor();
		barcodeScannerType = builder.getBarcodeScannerType();

		layoutType = builder.getLayoutType();
		widgetSize = builder.getWidgetSize();
		restrictToMediaTypes = builder.getRestrictToMediaTypes();

		gridAlign = builder.getGridAlign();
		viewEditorRenderMode = builder.getViewEditorRenderMode();
		viewAllowSorting = builder.isViewAllowSorting();

		advancedField = builder.isAdvancedField();

		fields = ImmutableSet.copyOf(builder.buildFields());
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("internalName", internalName)
				.add("caption", caption)
				.add("description", description)
				.add("widgetType", widgetType)
				.add("advancedField", advancedField)
				.add("fields", fields.isEmpty() ? null : fields)
				.toString();
	}

	public boolean isGridElement()
	{
		return gridElement;
	}

	public String getCaption(final String adLanguage)
	{
		return caption.translate(adLanguage);
	}

	public String getCaptionAsFieldNames()
	{
		if (_captionAsFieldNames == null)
		{
			_captionAsFieldNames = fields
					.stream()
					.filter(field -> field.isPublicField()) // only those which are public
					.map(field -> field.getField())
					.collect(GuavaCollectors.toString(JOINER_FieldNames));
		}
		return _captionAsFieldNames;
	}

	public String getDescription(final String adLanguage)
	{
		return description.translate(adLanguage);
	}

	public DocumentFieldWidgetType getWidgetType()
	{
		return widgetType;
	}

	public Set<MediaType> getRestrictToMediaTypes()
	{
		return restrictToMediaTypes;
	}

	public boolean isAllowShowPassword()
	{
		return allowShowPassword;
	}

	public boolean isMultilineText()
	{
		return multilineText;
	}

	public int getMultilineTextLines()
	{
		return multilineTextLines;
	}

	public LayoutType getLayoutType()
	{
		return layoutType;
	}

	public WidgetSize getWidgetSize()
	{
		return widgetSize;
	}

	public LayoutAlign getGridAlign()
	{
		return gridAlign;
	}

	public ViewEditorRenderMode getViewEditorRenderMode()
	{
		return viewEditorRenderMode;
	}

	public boolean isViewAllowSorting()
	{
		return viewAllowSorting;
	}

	public boolean isAdvancedField()
	{
		return advancedField;
	}

	public Set<DocumentLayoutElementFieldDescriptor> getFields()
	{
		return fields;
	}

	public boolean hasFields()
	{
		return !fields.isEmpty();
	}

	public String getFirstFieldName()
	{
		return fields.iterator().next().getField();
	}

	public ButtonFieldActionDescriptor getButtonActionDescriptor()
	{
		return buttonActionDescriptor;
	}

	public BarcodeScannerType getBarcodeScannerType()
	{
		return barcodeScannerType;
	}

	public static final class Builder
	{
		private static final Logger logger = LogManager.getLogger(DocumentLayoutElementDescriptor.Builder.class);

		private String _internalName;
		private ITranslatableString _caption = null;
		private ITranslatableString _description = null;

		private DocumentFieldWidgetType _widgetType;
		private boolean _allowShowPassword = false; // in case widgetType is Password
		private boolean _multilineText = false; // in case widgetType is Text
		private int _multilineTextLines = 0; // in case widgetType is Text
		private ButtonFieldActionDescriptor buttonActionDescriptor;
		private BarcodeScannerType barcodeScannerType;

		private LayoutType _layoutType;
		private WidgetSize _widgetSize;
		private Set<MediaType> restrictToMediaTypes = new HashSet<>();

		private boolean _gridElement = false;
		private ViewEditorRenderMode viewEditorRenderMode = null;
		private boolean viewAllowSorting = true;

		private boolean _advancedField = false;
		private final LinkedHashMap<String, DocumentLayoutElementFieldDescriptor.Builder> _fieldsBuilders = new LinkedHashMap<>();
		private boolean excludeSpecialFields = false;
		private boolean consumed = false;

		private Builder()
		{
		}

		@Override
		public String toString()
		{
			return MoreObjects.toStringHelper(this)
					.omitNullValues()
					.add("internalName", _internalName)
					.add("caption", _caption)
					.add("description", _description)
					.add("widgetType", _widgetType)
					.add("consumed", consumed ? Boolean.TRUE : null)
					.add("fields-count", _fieldsBuilders.size())
					.toString();
		}

		public DocumentLayoutElementDescriptor build()
		{
			setConsumed();

			final DocumentLayoutElementDescriptor result = new DocumentLayoutElementDescriptor(this);

			logger.trace("Built {} for {}", result, this);
			return result;
		}

		private Set<DocumentLayoutElementFieldDescriptor> buildFields()
		{
			return _fieldsBuilders
					.values()
					.stream()
					.filter(fieldBuilder -> checkValid(fieldBuilder))
					.map(fieldBuilder -> fieldBuilder.build())
					.collect(GuavaCollectors.toImmutableSet());
		}

		private boolean checkValid(final DocumentLayoutElementFieldDescriptor.Builder fieldBuilder)
		{
			if (fieldBuilder.isConsumed())
			{
				logger.trace("Skip adding {} to {} because it's already consumed", fieldBuilder, this);
				return false;
			}

			if (!fieldBuilder.isPublicField())
			{
				logger.trace("Skip adding {} to {} because it's not a public field", fieldBuilder, this);
				return false;
			}

			if (excludeSpecialFields && fieldBuilder.isSpecialFieldToExcludeFromLayout())
			{
				logger.trace("Skip adding {} to {} because it's a special field and we were asked to exclude special fields", fieldBuilder, this);
				return false;
			}

			return true;
		}

		public Builder setInternalName(final String internalName)
		{
			_internalName = internalName;
			return this;
		}

		private String getInternalName()
		{
			return _internalName;
		}

		public Builder setCaption(final ITranslatableString caption)
		{
			_caption = caption == null ? TranslatableStrings.empty() : caption;
			return this;
		}

		public Builder setCaption(final String caption)
		{
			setCaption(TranslatableStrings.constant(caption));
			return this;
		}

		public Builder setCaptionFromAD_Message(final String adMessage)
		{
			setCaption(Services.get(IMsgBL.class).translatable(adMessage));
			return this;
		}

		public Builder setCaptionNone()
		{
			setCaption(TranslatableStrings.empty());
			return this;
		}

		private ITranslatableString getCaption()
		{
			if (_caption != null)
			{
				return _caption;
			}

			final DocumentLayoutElementFieldDescriptor.Builder firstField = getFirstField();
			if (firstField != null)
			{
				final String fieldName = firstField.getFieldName();
				return Services.get(IMsgBL.class).translatable(fieldName);
			}

			return TranslatableStrings.empty();
		}

		public Builder setDescription(final ITranslatableString description)
		{
			_description = TranslatableStrings.nullToEmpty(description);
			return this;
		}

		private ITranslatableString getDescription()
		{
			return TranslatableStrings.nullToEmpty(_description);
		}

		public Builder setWidgetType(final DocumentFieldWidgetType widgetType)
		{
			_widgetType = widgetType;
			return this;
		}

		public boolean isWidgetTypeSet()
		{
			return _widgetType != null;
		}

		public boolean isWidgetSizeSet()
		{
			return _widgetSize != null;
		}

		public DocumentFieldWidgetType getWidgetType()
		{
			Check.assumeNotNull(_widgetType, DocumentLayoutBuildException.class, "Parameter widgetType is not null for {}", this);
			return _widgetType;
		}

		public Builder restrictToMediaType(@NonNull final MediaType mediaType)
		{
			restrictToMediaTypes.add(mediaType);
			return this;
		}

		public Builder restrictToMediaTypes(@NonNull final Collection<MediaType> mediaTypes)
		{
			restrictToMediaTypes.addAll(mediaTypes);
			return this;
		}

		private ImmutableSet<MediaType> getRestrictToMediaTypes()
		{
			return ImmutableSet.copyOf(restrictToMediaTypes);
		}

		public Builder setAllowShowPassword(boolean allowShowPassword)
		{
			this._allowShowPassword = allowShowPassword;
			return this;
		}

		private boolean isAllowShowPassword()
		{
			return _allowShowPassword;
		}

		public Builder setMultilineText(boolean multilineText)
		{
			this._multilineText = multilineText;
			return this;
		}

		private boolean isMultilineText()
		{
			return _multilineText;
		}

		public Builder setMultilineTextLines(final int multilineTextLines)
		{
			this._multilineTextLines = multilineTextLines;
			return this;
		}

		private int getMultilineTextLines()
		{
			return _multilineTextLines;
		}

		public Builder setLayoutType(final LayoutType layoutType)
		{
			_layoutType = layoutType;
			return this;
		}

		public Builder setLayoutTypeNone()
		{
			_layoutType = null;
			return this;
		}

		private LayoutType getLayoutType()
		{
			return _layoutType;
		}

		public Builder setWidgetSize(final WidgetSize widgetSize)
		{
			_widgetSize = widgetSize;
			return this;
		}

		private WidgetSize getWidgetSize()
		{
			return _widgetSize;
		}

		public Builder setAdvancedField(final boolean advancedField)
		{
			_advancedField = advancedField;
			return this;
		}

		public boolean isAdvancedField()
		{
			return _advancedField;
		}

		public Builder removeFieldByFieldName(final String fieldName)
		{
			_fieldsBuilders.remove(fieldName);
			return this;
		}

		public Builder addField(@NonNull final DocumentLayoutElementFieldDescriptor.Builder fieldBuilder)
		{
			final DocumentLayoutElementFieldDescriptor.Builder previousFieldBuilder = _fieldsBuilders.put(fieldBuilder.getFieldName(), fieldBuilder);
			if (previousFieldBuilder != null)
			{
				new AdempiereException("Field " + fieldBuilder.getFieldName() + " already exists in element: " + this)
						.throwIfDeveloperModeOrLogWarningElse(logger);
			}
			return this;
		}

		public Set<String> getFieldNames()
		{
			return _fieldsBuilders.keySet();
		}

		public DocumentLayoutElementFieldDescriptor.Builder getField(final String fieldName)
		{
			return _fieldsBuilders.get(fieldName);
		}

		public DocumentLayoutElementFieldDescriptor.Builder getFirstField()
		{
			return _fieldsBuilders.values().iterator().next();
		}

		public int getFieldsCount()
		{
			return _fieldsBuilders.size();
		}

		public boolean hasFieldName(final String fieldName)
		{
			return _fieldsBuilders.containsKey(fieldName);
		}

		public Builder setExcludeSpecialFields()
		{
			excludeSpecialFields = true;
			return this;
		}

		private Builder setConsumed()
		{
			consumed = true;
			return this;
		}

		public boolean isConsumed()
		{
			return consumed;
		}

		public boolean isGridElement()
		{
			return _gridElement;
		}

		/**
		 * Flags this element as a "grid element".
		 */
		public Builder setGridElement()
		{
			_gridElement = true;
			return this;
		}

		/**
		 * Reset the "grid element" flag.
		 *
		 * NOTE: this is false by default, but the main purpose of this method is intention revealing.
		 *
		 * @see #setGridElement()
		 */
		public Builder setNotGridElement()
		{
			_gridElement = false;
			return this;
		}

		private LayoutAlign getGridAlign()
		{
			return isGridElement() ? getWidgetType().getGridAlign() : null;
		}

		public Builder setViewEditorRenderMode(final ViewEditorRenderMode gridEditorRenderMode)
		{
			this.viewEditorRenderMode = gridEditorRenderMode;
			return this;
		}

		private ViewEditorRenderMode getViewEditorRenderMode()
		{
			return viewEditorRenderMode;
		}

		public Builder setViewAllowSorting(boolean viewAllowSorting)
		{
			this.viewAllowSorting = viewAllowSorting;
			return this;
		}

		private boolean isViewAllowSorting()
		{
			return viewAllowSorting;
		}

		public Builder setButtonActionDescriptor(final ButtonFieldActionDescriptor buttonActionDescriptor)
		{
			this.buttonActionDescriptor = buttonActionDescriptor;
			return this;
		}

		/* package */ ButtonFieldActionDescriptor getButtonActionDescriptor()
		{
			return buttonActionDescriptor;
		}

		public Builder barcodeScannerType(final BarcodeScannerType barcodeScannerType)
		{
			this.barcodeScannerType = barcodeScannerType;
			return this;
		}

		private BarcodeScannerType getBarcodeScannerType()
		{
			return barcodeScannerType;
		}
	}
}
