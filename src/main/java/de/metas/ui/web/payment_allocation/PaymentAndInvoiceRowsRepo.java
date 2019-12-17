package de.metas.ui.web.payment_allocation;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner;
import org.springframework.stereotype.Repository;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.banking.payment.paymentallocation.InvoiceToAllocate;
import de.metas.banking.payment.paymentallocation.InvoiceToAllocateQuery;
import de.metas.banking.payment.paymentallocation.InvoiceToAllocateQuery.InvoiceToAllocateQueryBuilder;
import de.metas.banking.payment.paymentallocation.PaymentAllocationRepository;
import de.metas.banking.payment.paymentallocation.PaymentToAllocate;
import de.metas.banking.payment.paymentallocation.PaymentToAllocateQuery;
import de.metas.bpartner.BPartnerId;
import de.metas.currency.CurrencyCode;
import de.metas.currency.CurrencyRepository;
import de.metas.document.IDocTypeBL;
import de.metas.invoice.InvoiceId;
import de.metas.money.CurrencyId;
import de.metas.payment.PaymentId;
import de.metas.ui.web.window.model.lookup.LookupDataSource;
import de.metas.ui.web.window.model.lookup.LookupDataSourceFactory;
import de.metas.util.Check;
import de.metas.util.Services;
import de.metas.util.time.SystemTime;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2019 metas GmbH
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

@Repository
public class PaymentAndInvoiceRowsRepo
{
	private final IDocTypeBL docTypeBL = Services.get(IDocTypeBL.class);
	private final CurrencyRepository currenciesRepo;
	private final PaymentAllocationRepository paymentAllocationRepo;
	private final LookupDataSource bpartnersLookup;

	public PaymentAndInvoiceRowsRepo(
			@NonNull final CurrencyRepository currenciesRepo,
			@NonNull final PaymentAllocationRepository paymentAllocationRepo)
	{
		this.currenciesRepo = currenciesRepo;
		this.paymentAllocationRepo = paymentAllocationRepo;
		bpartnersLookup = LookupDataSourceFactory.instance.searchInTableLookup(I_C_BPartner.Table_Name);
	}

	public PaymentAndInvoiceRows getByPaymentIds(@NonNull final Set<PaymentId> paymentIds)
	{
		Check.assumeNotEmpty(paymentIds, "paymentIds is not empty");

		final ZonedDateTime evaluationDate = SystemTime.asZonedDateTime();

		final List<PaymentToAllocate> paymentsToAllocate = paymentAllocationRepo.retrievePaymentsToAllocate(PaymentToAllocateQuery.builder()
				.evaluationDate(evaluationDate)
				.additionalPaymentIdsToInclude(paymentIds)
				.build());
		if (paymentsToAllocate.isEmpty())
		{
			throw new AdempiereException("@NoOpenPayments@");
		}

		final PaymentRows paymentRows = toPaymentRows(paymentsToAllocate, evaluationDate);
		final InvoiceRows invoiceRows = retrieveInvoiceRowsByPayments(paymentsToAllocate, evaluationDate);

		return PaymentAndInvoiceRows.builder()
				.paymentRows(paymentRows)
				.invoiceRows(invoiceRows)
				.build();
	}

	private PaymentRows toPaymentRows(
			final List<PaymentToAllocate> paymentsToAllocate,
			final ZonedDateTime evaluationDate)
	{
		final ImmutableList<PaymentRow> rows = paymentsToAllocate
				.stream()
				.map(this::toPaymentRow)
				.collect(ImmutableList.toImmutableList());

		return PaymentRows.builder()
				.repository(this)
				.evaluationDate(evaluationDate)
				.initialRows(rows)
				.build();
	}

	private PaymentRow toPaymentRow(final PaymentToAllocate paymentToAllocate)
	{
		final BPartnerId bpartnerId = paymentToAllocate.getBpartnerId();

		return PaymentRow.builder()
				.paymentId(paymentToAllocate.getPaymentId())
				.clientAndOrgId(paymentToAllocate.getClientAndOrgId())
				.documentNo(paymentToAllocate.getDocumentNo())
				.bpartner(bpartnersLookup.findById(bpartnerId))
				.dateTrx(paymentToAllocate.getDateTrx())
				.payAmt(paymentToAllocate.getPayAmt())
				.openAmt(paymentToAllocate.getOpenAmt())
				.inboundPayment(paymentToAllocate.isInboundPayment())
				.build();
	}

	private InvoiceRows retrieveInvoiceRowsByPayments(
			final List<PaymentToAllocate> paymentsToAllocate,
			final ZonedDateTime evaluationDate)
	{
		final ImmutableSet<InvoiceToAllocateQuery> queries = paymentsToAllocate.stream()
				.map(paymentToAllocate -> prepareInvoiceToAllocateQuery(paymentToAllocate)
						.evaluationDate(evaluationDate)
						.build())
				.collect(ImmutableSet.toImmutableSet());

		final ImmutableList<InvoiceRow> rows = paymentAllocationRepo.retrieveInvoicesToAllocate(queries)
				.stream()
				.map(this::toInvoiceRow)
				.collect(ImmutableList.toImmutableList());

		return InvoiceRows.builder()
				.repository(this)
				.evaluationDate(evaluationDate)
				.initialRows(rows)
				.build();
	}

	private InvoiceToAllocateQueryBuilder prepareInvoiceToAllocateQuery(final PaymentToAllocate paymentToAllocate)
	{
		final CurrencyCode currencyCode = paymentToAllocate.getOpenAmt().getCurrencyCode();
		final CurrencyId currencyId = currenciesRepo.getCurrencyIdByCurrencyCode(currencyCode);

		return InvoiceToAllocateQuery.builder()
				.bpartnerId(paymentToAllocate.getBpartnerId())
				.currencyId(currencyId)
				.clientAndOrgId(paymentToAllocate.getClientAndOrgId());
	}

	private InvoiceRow toInvoiceRow(final InvoiceToAllocate invoiceToAllocate)
	{
		return InvoiceRow.builder()
				.invoiceId(invoiceToAllocate.getInvoiceId())
				.clientAndOrgId(invoiceToAllocate.getClientAndOrgId())
				.docTypeName(docTypeBL.getNameById(invoiceToAllocate.getDocTypeId()))
				.documentNo(invoiceToAllocate.getDocumentNo())
				.dateInvoiced(invoiceToAllocate.getDateInvoiced())
				.bpartner(bpartnersLookup.findById(invoiceToAllocate.getBpartnerId()))
				.soTrx(invoiceToAllocate.getSoTrx())
				.creditMemo(invoiceToAllocate.isCreditMemo())
				.grandTotal(invoiceToAllocate.getGrandTotal())
				.openAmt(invoiceToAllocate.getOpenAmountConverted())
				.discountAmt(invoiceToAllocate.getDiscountAmountConverted())
				.build();
	}

	public List<InvoiceRow> getInvoiceRowsListByInvoiceId(
			@NonNull final Collection<InvoiceId> invoiceIds,
			@NonNull final ZonedDateTime evaluationDate)
	{
		if (invoiceIds.isEmpty())
		{
			return ImmutableList.of();
		}

		final InvoiceToAllocateQuery query = InvoiceToAllocateQuery.builder()
				.evaluationDate(evaluationDate)
				.additionalInvoiceIdsToInclude(invoiceIds)
				.build();

		return paymentAllocationRepo.retrieveInvoicesToAllocate(query)
				.stream()
				.map(this::toInvoiceRow)
				.collect(ImmutableList.toImmutableList());
	}

	public Optional<InvoiceRow> getInvoiceRowByInvoiceId(
			@NonNull final InvoiceId invoiceId,
			@NonNull final ZonedDateTime evaluationDate)
	{
		final List<InvoiceRow> invoiceRows = getInvoiceRowsListByInvoiceId(ImmutableList.of(invoiceId), evaluationDate);
		if (invoiceRows.isEmpty())
		{
			return Optional.empty();
		}
		else if (invoiceRows.size() == 1)
		{
			return Optional.of(invoiceRows.get(0));
		}
		else
		{
			throw new AdempiereException("Expected only one row for " + invoiceId + " but got " + invoiceRows);
		}
	}

	public List<PaymentRow> getPaymentRowsListByPaymentId(
			@NonNull final Collection<PaymentId> paymentIds,
			@NonNull final ZonedDateTime evaluationDate)
	{
		if (paymentIds.isEmpty())
		{
			return ImmutableList.of();
		}

		final PaymentToAllocateQuery query = PaymentToAllocateQuery.builder()
				.evaluationDate(evaluationDate)
				.additionalPaymentIdsToInclude(paymentIds)
				.build();

		return paymentAllocationRepo.retrievePaymentsToAllocate(query)
				.stream()
				.map(this::toPaymentRow)
				.collect(ImmutableList.toImmutableList());
	}

	public Optional<PaymentRow> getPaymentRowByPaymentId(
			@NonNull final PaymentId paymentId,
			@NonNull final ZonedDateTime evaluationDate)
	{
		final List<PaymentRow> paymentRows = getPaymentRowsListByPaymentId(ImmutableList.of(paymentId), evaluationDate);
		if (paymentRows.isEmpty())
		{
			return Optional.empty();
		}
		else if (paymentRows.size() == 1)
		{
			return Optional.of(paymentRows.get(0));
		}
		else
		{
			throw new AdempiereException("Expected only one row for " + paymentId + " but got " + paymentRows);
		}
	}

}
