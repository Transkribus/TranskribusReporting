
package eu.transkribus.report;

import java.util.List;

import eu.transkribus.core.io.util.TrpProperties;

public interface ReportDatabaseInterface {

	static public void createReport(int time) {
		ReportFromDatabase.generateReport(time);
	}

	static public List<String> mailingList() {
		String[] mailingList = { "florian.krull@student.uibk.ac.at" };
		TrpProperties mailProp = new TrpProperties("email.properties");
		List<String> mailListProp = mailProp.getCsvStringListProperty("recipients", false);
		return mailListProp;
		// return mailingList;
	}

}
