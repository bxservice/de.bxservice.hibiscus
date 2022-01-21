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
 * - Carlos Ruiz                                                       *
 **********************************************************************/
package de.bxservice.hibiscus;

import org.adempiere.base.IBankStatementMatcherFactory;
import org.compiere.impexp.BankStatementMatcherInterface;

public class BankStatementMatcherFactory implements IBankStatementMatcherFactory {

	@Override
	public BankStatementMatcherInterface newBankStatementMatcherInstance(String className) {

		if (HibiscusMatcherCustomerInvoiceInMemo.class.getName().equals(className))
			return new HibiscusMatcherCustomerInvoiceInMemo();

		if (HibiscusMatcherVendorSEPAPayment.class.getName().equals(className))
			return new HibiscusMatcherVendorSEPAPayment();

		return null;

	}

}
