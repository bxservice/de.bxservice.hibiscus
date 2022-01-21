/***********************************************************************
 * This file is part of iDempiere ERP Open Source                      *
 * http://www.idempiere.org                                            *
 *                                                                     *
 * Copyright (C) Contributors                                          *
 *                                                                     *
 * This program is free software; you can redistribute it and/or       *
 * modify it under the terms of the GNU General Public License         *
 * as published by the Free Software Foundation; either version 2      *
 * of the License, or (at your option) any later version.              *
 *                                                                     *
 * This program is distributed in the hope that it will be useful,     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of      *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
 * GNU General Public License for more details.                        *
 *                                                                     *
 * You should have received a copy of the GNU General Public License   *
 * along with this program; if not, write to the Free Software         *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
 * MA 02110-1301, USA.                                                 *
 *                                                                     *
 * Contributors:                                                       *
 * - Carlos Ruiz - globalqss - BX Service                              *
 **********************************************************************/

package de.bxservice.hibiscus;

import java.sql.Timestamp;

import org.compiere.impexp.BankStatementMatchInfo;
import org.compiere.impexp.BankStatementMatcherInterface;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.MPayment;
import org.compiere.model.MSysConfig;
import org.compiere.model.Query;
import org.compiere.model.X_I_BankStatement;
import org.compiere.util.CLogger;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;

/**
 * This is a basic bank statement matcher that searches for Vendor SEPA Payments
 * 
 * @author Carlos Ruiz - globalqss - BX Service
 */
public class HibiscusMatcherVendorSEPAPayment implements BankStatementMatcherInterface {

	/**	Logger							*/
	protected CLogger			log = CLogger.getCLogger (getClass());

	/**
	 * 	Match Bank Statement Line
	 *	@param bsl bank statement line
	 *	@return found matches or null
	 */
	@Override
	public BankStatementMatchInfo findMatch(MBankStatementLine bsl) {
		BankStatementMatchInfo bsi = new BankStatementMatchInfo();
		if (bsl.getTrxAmt().signum() < 0)
			matchVendorSEPAPayment(bsi, bsl);
		return bsi;
	}

	private void matchVendorSEPAPayment(BankStatementMatchInfo bsi, MBankStatementLine bsl) {

		// Match a vendor payment
		// Vendor Payments are expected to be generated using de.bxservice.sepa plugin
		// fields are filled this way:
		//   Datum/Valuta -> ReqdExctnDt -> ~ C_PaySelection.PayDate
		//   TrxAmt -> Betrag -> InstdAmt -> C_PaySelectionCheck.PayAmt
		//   EftPayee -> Empfaenger_Name -> Cdtr -> C_BPartner.Name
		//   EftPayeeAccount -> Empfaenger_Konto -> CdtrAcct -> C_BP_BankAccount.IBAN
		// we are not checking here for invoice numbers, but in case required they are filled in:
		//   EftReference -> EndToEndId (separated by /)
		//   RmtInf/Ustrd -> with more information about order/date/invoice
		int daysRange = MSysConfig.getIntValue("BXS_DATE_RANGE_MATCHER", 0, bsl.getAD_Client_ID());
		Timestamp valutaFrom = TimeUtil.addDays(bsl.getValutaDate(), -daysRange);
		Timestamp valutaTo = TimeUtil.addDays(bsl.getValutaDate(), daysRange);
		Timestamp datumFrom = TimeUtil.addDays(bsl.getStatementLineDate(), -daysRange);
		Timestamp datumTo = TimeUtil.addDays(bsl.getStatementLineDate(), daysRange);
		final String whereClause =
				"C_Payment.IsReceipt='N' "
						+ "AND C_Payment.IsReconciled='N' "
						+ "AND C_Payment.DocStatus IN ('CO','CL') "
						+ "AND C_Payment.PayAmt=? "
						+ "AND bp.Name=? "
						+ "AND bpb.IBAN=? "
						+ "AND (C_Payment.DateTrx BETWEEN ? AND ? OR C_Payment.DateTrx BETWEEN ? AND ?)";
		MPayment payment = new Query(bsl.getCtx(), MPayment.Table_Name, whereClause, bsl.get_TrxName())
				.addJoinClause("JOIN C_BPartner bp ON (C_Payment.C_BPartner_ID=bp.C_BPartner_ID)")
				.addJoinClause("JOIN C_BP_BankAccount bpb ON (bp.C_BPartner_ID=bpb.C_BPartner_ID)")
				.setOrderBy(MPayment.COLUMNNAME_C_Payment_ID)
				.setParameters(bsl.getTrxAmt().negate(), bsl.getEftPayee(), bsl.getEftPayeeAccount(), datumFrom, datumTo, valutaFrom, valutaTo)
				.first();
		if (payment != null) {
			bsi.setC_Payment_ID(payment.getC_Payment_ID());
			bsi.setC_BPartner_ID(payment.getC_BPartner_ID());
			if (payment.getC_Invoice_ID() > 0)
				bsi.setC_Invoice_ID(payment.getC_Invoice_ID());
			addDescription(bsl, Msg.getMsg(bsl.getCtx(), "BXS_ExactMatch"));
		}
	}

	private void addDescription(MBankStatementLine bsl, String msg) {
		String description = bsl.getDescription();
		if (description == null)
			description = "";
		description = description.replaceAll("^¡ .* ¡ ", ""); // remove previous notice
		description = "¡ " + msg + " ¡ " + description;
		bsl.setDescription(description);
	}

	/**
	 * 	Match Bank Statement Import Line
	 *	@param ibs bank statement import line
	 *	@return found matches or null
	 */
	@Override
	public BankStatementMatchInfo findMatch(X_I_BankStatement ibs) {
		log.warning("Not implemented for import bank statement");
		return null;
	}

}
