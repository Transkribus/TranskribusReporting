package eu.transkribus.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	
	public static void main(String[] args) {
		
		final String testName;
		if(args != null && args.length > 0) {
			testName = args[0];
		} else {
			logger.error("No arguments given. Exiting.");
			return;
		}
		
	}
}
