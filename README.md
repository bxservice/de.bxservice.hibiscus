[BX Service GmbH](https://www.bx-service.com/) bank interface with [Hibiscus](https://www.willuhn.de/)

* Bank statement loader importer for [iDempiere](https://github.com/idempiere/idempiere) Open Source ERP.

* Import files from a modified CSV format from [Hibiscus](https://www.willuhn.de/) containing all information from the table umsatz

* See the modified velocity format at HibiscusCSVVelocityFormat/de.willuhn.jameica.hbci.rmi.Umsatz.csv.vm

* Replace the file de.willuhn.jameica.hbci.rmi.Umsatz.csv.vm in folder $HOME/.jameica/plugins/hibiscus/lib/velocity
    * This file is overwritten on every Hibiscus update, so it must be copied again after updating.

* Bank Statement Matcher for Customer Invoices in Memo field, as expected when importing from Hibiscus with this plugin
    * Class de.bxservice.hibiscus.HibiscusMatcherCustomerInvoiceInMemo

* Bank Statement Matcher for Vendor SEPA Payments, as expected when importing from Hibiscus with this plugin, payments generated using the [de.bxservice.sepa plugin](https://github.com/bxservice/de.bxservice.sepa)
    * Class de.bxservice.hibiscus.HibiscusMatcherVendorSEPAPayment
