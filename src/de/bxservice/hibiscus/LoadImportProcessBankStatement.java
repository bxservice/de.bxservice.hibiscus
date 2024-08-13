/**********************************************************************
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
 * - Carlos Ruiz - globalqss - bxservice                               *
 **********************************************************************/

package de.bxservice.hibiscus;

import java.math.BigDecimal;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.ProcessUtil;
import org.compiere.model.MBankAccount;
import org.compiere.model.MBankStatement;
import org.compiere.model.MBankStatementLoader;
import org.compiere.model.MPInstance;
import org.compiere.model.MProcess;
import org.compiere.model.MProcessPara;
import org.compiere.model.Query;
import org.compiere.model.X_I_BankStatement;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoLog;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

@org.adempiere.base.annotation.Process
public class LoadImportProcessBankStatement extends SvrProcess {

	/* Bank Statement Loader */
	private int p_C_BankStatementLoader_ID = 0;
	/* File Name */
	private String p_FileName = null;
	/* Match Bank Statement */
	private Boolean p_BAY_IsMatchBS = null;
	/* Create Payments from Bank Statement */
	private Boolean p_BAY_IsCreatePaymentsBS = null;

	/* Bank Account */
	private int p_C_BankAccount_ID = 0;

	/* Process IDs */
	public final static int PROCESS_DELETE_IMPORT = 248;
	public final static int PROCESS_LOAD_BANK_STATEMENT = 247;
	public final static int PROCESS_IMPORT_BANK_STATEMENT = 221;
	public final static int PROCESS_MATCH_BANK_STATEMENT = 256;
	public final static int PROCESS_CREATE_PAYMENT = 257;

	@Override
	protected void prepare() {
		for (ProcessInfoParameter para : getParameter()) {
			String name = para.getParameterName();
			switch (name) {
			case "C_BankStatementLoader_ID":
				p_C_BankStatementLoader_ID = para.getParameterAsInt();
				break;
			case "FileName":
				p_FileName = para.getParameterAsString();
				break;
			case "BAY_IsMatchBS":
				p_BAY_IsMatchBS = para.getParameterAsBoolean();
				break;
			case "BAY_IsCreatePaymentsBS":
				p_BAY_IsCreatePaymentsBS = para.getParameterAsBoolean();
				break;
			default:
				MProcessPara.validateUnknownParameter(getProcessInfo().getAD_Process_ID(), para);
			}
		}
	}

	/**
	 * Perform process.
	 * 
	 * @return Message
	 * @throws Exception
	 */
	protected String doIt() throws java.lang.Exception {

		deleteImport();

		MBankStatementLoader bsl = new MBankStatementLoader(getCtx(), p_C_BankStatementLoader_ID, get_TrxName());
		p_C_BankAccount_ID = bsl.getC_BankAccount_ID();

		loadBankStatement();

		importBankStatement();

		MBankStatement bs = new Query(getCtx(), MBankStatement.Table_Name, "C_BankAccount_ID=? AND DocStatus='DR'", get_TrxName())
				.setOrderBy("C_BankStatement_ID DESC")
				.setParameters(p_C_BankAccount_ID)
				.first();

		if (p_BAY_IsMatchBS)
			matchBankStatement(bs);

		if (p_BAY_IsMatchBS && p_BAY_IsCreatePaymentsBS)
			createPayments(bs);

		return "@OK@";
	}

	private void deleteImport() {
		int processIdDI = PROCESS_DELETE_IMPORT;
		MProcess procDI = new MProcess(getCtx(), processIdDI, get_TrxName());

		MPInstance instanceDI = new MPInstance(procDI, 0, -1, null);
		instanceDI.saveEx();
		ProcessInfo poInfoDI = new ProcessInfo(procDI.getName(), procDI.getAD_Process_ID());
		ProcessInfoParameter pipTbl = new ProcessInfoParameter("AD_Table_ID", BigDecimal.valueOf(X_I_BankStatement.Table_ID), null, String.valueOf(X_I_BankStatement.Table_ID), null);
		poInfoDI.setParameter(new ProcessInfoParameter[] {pipTbl});
		poInfoDI.setAD_Process_ID(procDI.getAD_Process_ID());
		poInfoDI.setAD_PInstance_ID(instanceDI.getAD_PInstance_ID());
		poInfoDI.setAD_Process_UU(procDI.getAD_Process_UU());
		poInfoDI.setClassName(procDI.getClassname());

		ProcessUtil.startJavaProcess(getCtx(), poInfoDI, Trx.get(get_TrxName(), false), false);

		if (poInfoDI.isError())
			throw new AdempiereException(Msg.getMsg(getCtx(), "Error") + " " + procDI.get_Translation(MProcess.COLUMNNAME_Name) + " -> " +  poInfoDI.getSummary());
		addBufferLog(0, null, null, "** " + procDI.get_Translation(MProcess.COLUMNNAME_Name) + " **", 0, -1);
		if (poInfoDI.getLogs() != null)
			for (ProcessInfoLog log : poInfoDI.getLogs())
				addBufferLog(log.getP_ID(), log.getP_Date(), log.getP_Number(), log.getP_Msg(), log.getAD_Table_ID(), log.getRecord_ID());
		addBufferLog(0, null, null, poInfoDI.getSummary(), 0, -1);
	}

	private void loadBankStatement() {
		int processIdLBS = PROCESS_LOAD_BANK_STATEMENT;
		MProcess procLBS = new MProcess(getCtx(), processIdLBS, get_TrxName());

		MPInstance instanceLBS = new MPInstance(procLBS, 0, -1, null);
		instanceLBS.saveEx();
		ProcessInfo poInfoLBS = new ProcessInfo(procLBS.getName(), procLBS.getAD_Process_ID());
		ProcessInfoParameter pipBSL = new ProcessInfoParameter("C_BankStatementLoader_ID", BigDecimal.valueOf(p_C_BankStatementLoader_ID), null, String.valueOf(p_C_BankStatementLoader_ID), null);
		ProcessInfoParameter pipFN = new ProcessInfoParameter("FileName", p_FileName, null, p_FileName, null);
		poInfoLBS.setParameter(new ProcessInfoParameter[] {pipBSL, pipFN});
		poInfoLBS.setAD_Process_ID(procLBS.getAD_Process_ID());
		poInfoLBS.setAD_PInstance_ID(instanceLBS.getAD_PInstance_ID());
		poInfoLBS.setAD_Process_UU(procLBS.getAD_Process_UU());
		poInfoLBS.setClassName(procLBS.getClassname());

		ProcessUtil.startJavaProcess(getCtx(), poInfoLBS, Trx.get(get_TrxName(), false), false);

		if (poInfoLBS.isError())
			throw new AdempiereException(Msg.getMsg(getCtx(), "Error") + " " + procLBS.get_Translation(MProcess.COLUMNNAME_Name) + " -> " +  poInfoLBS.getSummary());
		addBufferLog(0, null, null, "** " + procLBS.get_Translation(MProcess.COLUMNNAME_Name) + " **", 0, -1);
		if (poInfoLBS.getLogs() != null)
			for (ProcessInfoLog log : poInfoLBS.getLogs())
				addBufferLog(log.getP_ID(), log.getP_Date(), log.getP_Number(), log.getP_Msg(), log.getAD_Table_ID(), log.getRecord_ID());
		addBufferLog(0, null, null, poInfoLBS.getSummary(), 0, -1);
	}

	private void importBankStatement() {
		int processIdIBS = PROCESS_IMPORT_BANK_STATEMENT;
		MProcess procIBS = new MProcess(getCtx(), processIdIBS, get_TrxName());

		MPInstance instanceIBS = new MPInstance(procIBS, 0, -1, null);
		instanceIBS.saveEx();

		MBankAccount bankAccount = MBankAccount.get(p_C_BankAccount_ID);

		ProcessInfo poInfoIBS = new ProcessInfo(procIBS.getName(), procIBS.getAD_Process_ID());
		ProcessInfoParameter pipCl = new ProcessInfoParameter("AD_Client_ID", BigDecimal.valueOf(getAD_Client_ID()), null, String.valueOf(getAD_Client_ID()), null);
		ProcessInfoParameter pipOrg = new ProcessInfoParameter("AD_Org_ID", BigDecimal.valueOf(bankAccount.getAD_Org_ID()), null, String.valueOf(bankAccount.getAD_Org_ID()), null);
		ProcessInfoParameter pipBA = new ProcessInfoParameter("C_BankAccount_ID", BigDecimal.valueOf(p_C_BankAccount_ID), null, String.valueOf(p_C_BankAccount_ID), null);
		ProcessInfoParameter pipDel = new ProcessInfoParameter("DeleteOldImported", "Y", null, "Y", null);
		poInfoIBS.setParameter(new ProcessInfoParameter[] {pipCl, pipOrg, pipBA, pipDel});
		poInfoIBS.setAD_Process_ID(procIBS.getAD_Process_ID());
		poInfoIBS.setAD_PInstance_ID(instanceIBS.getAD_PInstance_ID());
		poInfoIBS.setAD_Process_UU(procIBS.getAD_Process_UU());
		poInfoIBS.setClassName(procIBS.getClassname());

		ProcessUtil.startJavaProcess(getCtx(), poInfoIBS, Trx.get(get_TrxName(), false), false);

		if (poInfoIBS.isError())
			throw new AdempiereException(Msg.getMsg(getCtx(), "Error") + " " + procIBS.get_Translation(MProcess.COLUMNNAME_Name) + " -> " +  poInfoIBS.getSummary());
		addBufferLog(0, null, null, "** " + procIBS.get_Translation(MProcess.COLUMNNAME_Name) + " **", 0, -1);
		if (poInfoIBS.getLogs() != null)
			for (ProcessInfoLog log : poInfoIBS.getLogs())
				addBufferLog(log.getP_ID(), log.getP_Date(), log.getP_Number(), Msg.parseTranslation(getCtx(), log.getP_Msg()), log.getAD_Table_ID(), log.getRecord_ID());
		addBufferLog(0, null, null, poInfoIBS.getSummary(), 0, -1);
	}

	private void matchBankStatement(MBankStatement bs) {
		int processIdMBS = PROCESS_MATCH_BANK_STATEMENT;
		MProcess procMBS = new MProcess(getCtx(), processIdMBS, get_TrxName());

		MPInstance instanceMBS = new MPInstance(procMBS, MBankStatement.Table_ID, bs.getC_BankStatement_ID(), bs.getC_BankStatement_UU());
		instanceMBS.saveEx();

		ProcessInfo poInfoMBS = new ProcessInfo(procMBS.getName(), procMBS.getAD_Process_ID());
		poInfoMBS.setTable_ID(MBankStatement.Table_ID);
		poInfoMBS.setRecord_ID(bs.getC_BankStatement_ID());
		poInfoMBS.setAD_Process_ID(procMBS.getAD_Process_ID());
		poInfoMBS.setAD_PInstance_ID(instanceMBS.getAD_PInstance_ID());
		poInfoMBS.setAD_Process_UU(procMBS.getAD_Process_UU());
		poInfoMBS.setClassName(procMBS.getClassname());

		ProcessUtil.startJavaProcess(getCtx(), poInfoMBS, Trx.get(get_TrxName(), false), false);

		if (poInfoMBS.isError())
			throw new AdempiereException(Msg.getMsg(getCtx(), "Error") + " " + procMBS.get_Translation(MProcess.COLUMNNAME_Name) + " -> " +  poInfoMBS.getSummary());
		addBufferLog(0, null, null, "** " + procMBS.get_Translation(MProcess.COLUMNNAME_Name) + " " + bs.getDocumentNo() + " **", MBankStatement.Table_ID, bs.getC_BankStatement_ID());
		if (poInfoMBS.getLogs() != null)
			for (ProcessInfoLog log : poInfoMBS.getLogs())
				addBufferLog(log.getP_ID(), log.getP_Date(), log.getP_Number(), log.getP_Msg(), log.getAD_Table_ID(), log.getRecord_ID());
		addBufferLog(0, null, null, poInfoMBS.getSummary(), 0, -1);
	}

	private void createPayments(MBankStatement bs) {
		int processIdCP = PROCESS_CREATE_PAYMENT;
		MProcess procCP = new MProcess(getCtx(), processIdCP, get_TrxName());

		MPInstance instanceCP = new MPInstance(procCP, MBankStatement.Table_ID, bs.getC_BankStatement_ID(), bs.getC_BankStatement_UU());
		instanceCP.saveEx();

		ProcessInfo poInfoCP = new ProcessInfo(procCP.getName(), procCP.getAD_Process_ID());
		poInfoCP.setTable_ID(MBankStatement.Table_ID);
		poInfoCP.setRecord_ID(bs.getC_BankStatement_ID());
		poInfoCP.setAD_Process_ID(procCP.getAD_Process_ID());
		poInfoCP.setAD_PInstance_ID(instanceCP.getAD_PInstance_ID());
		poInfoCP.setAD_Process_UU(procCP.getAD_Process_UU());
		poInfoCP.setClassName(procCP.getClassname());

		ProcessUtil.startJavaProcess(getCtx(), poInfoCP, Trx.get(get_TrxName(), false), false);

		if (poInfoCP.isError())
			throw new AdempiereException(Msg.getMsg(getCtx(), "Error") + " " + procCP.get_Translation(MProcess.COLUMNNAME_Name) + " -> " +  poInfoCP.getSummary());
		addBufferLog(0, null, null, "** " + procCP.get_Translation(MProcess.COLUMNNAME_Name) + " " + bs.getDocumentNo() + " **", MBankStatement.Table_ID, bs.getC_BankStatement_ID());
		if (poInfoCP.getLogs() != null)
			for (ProcessInfoLog log : poInfoCP.getLogs())
				addBufferLog(log.getP_ID(), log.getP_Date(), log.getP_Number(), log.getP_Msg(), log.getAD_Table_ID(), log.getRecord_ID());
		addBufferLog(0, null, null, poInfoCP.getSummary(), 0, -1);
	}

}