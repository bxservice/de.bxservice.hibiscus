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

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MBankStatement;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Msg;
import org.osgi.service.event.Event;

/**
 * This event handler for Hibiscus integration
 * 
 * @author Carlos Ruiz - globalqss - BX Service
 */
public class EventHandler extends AbstractEventHandler {
	/** Logger */
	private static CLogger log = CLogger.getCLogger(EventHandler.class);

	/**
	 * Initialize Validation
	 */
	@Override
	protected void initialize() {
		log.info("");

		registerTableEvent(IEventTopics.DOC_BEFORE_PREPARE, MBankStatement.Table_Name);
	} // initialize

	/**
	 * Model Change of a monitored Table.
	 * 
	 * @param event
	 * @exception Exception if the recipient wishes the change to be not accept.
	 */
	@Override
	protected void doHandleEvent(Event event) {
		String type = event.getTopic();

		PO po = getPO(event);
		log.info(po + " Type: " + type);
		String msg;

		if (po instanceof MBankStatement && type.equals(IEventTopics.DOC_BEFORE_PREPARE)) {
			MBankStatement bs = (MBankStatement) po;
			msg = validate(bs);
			if (msg != null)
				throw new RuntimeException(msg);
		}

	} // doHandleEvent

	/**
	 * Validate bank statement
	 * do not allow prepare a bank statement if the trxamt<>0 and c_payment_id=0 and c_invoice_id=0 <- must match or set a charge or interest
	 * do not allow prepare a bank statement if the trxamt<>0 and c_payment_id=0 and c_invoice_id>0 <- must create payment first
	 *
	 * @param bs bank statement
	 * @return error message or null
	 */
	public String validate(MBankStatement bs) {
		log.info("");

		StringBuilder linesToMatch = new StringBuilder();
		StringBuilder linesToPay = new StringBuilder();
		for (MBankStatementLine bsl : bs.getLines(true)) {
			if (bsl.getTrxAmt().signum() != 0 && bsl.getC_Payment_ID() == 0) {
				if (bsl.getC_Invoice_ID() <= 0) {
					if (linesToMatch.length() > 0)
						linesToMatch.append(", ");
					linesToMatch.append(bsl.getLine());
				} else {
					if (linesToPay.length() > 0)
						linesToPay.append(", ");
					linesToPay.append(bsl.getLine());
				}
			}
		}

		if (linesToMatch.length() == 0 && linesToPay.length() == 0)
			return null;

		StringBuilder msg = new StringBuilder();
		if (linesToMatch.length() > 0) {
			msg.append(Msg.getMsg(bs.getCtx(), "BXS_LineMustBeMatched", new Object[] {linesToMatch.length(), linesToMatch}));
		}
		if (linesToPay.length() > 0) {
			if (msg.length() > 0)
				msg.append(" + ");
			msg.append(Msg.getMsg(bs.getCtx(), "BXS_LineMustMatchPayment", new Object[] {linesToPay.length(), linesToPay}));
		}

		return msg.toString();
	} // validate

} // EventHandler
