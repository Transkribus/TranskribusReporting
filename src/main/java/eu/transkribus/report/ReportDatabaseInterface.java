package eu.transkribus.report;

public interface ReportDatabaseInterface {

	static public void createReport(int time) {
		ReportFromDatabase.generateReport(time);
	}

	static public String[] mailingList() {
		String[] mailingList = { "florian.krull@student.uibk.ac.at" };
		return mailingList;
	}

}
