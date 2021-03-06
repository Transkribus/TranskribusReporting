package eu.transkribus.report;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xml.bind.v2.runtime.RuntimeUtil;

import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.DbConnection.DbConfig;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.util.MailUtils;

/*
 * 
 * Mailing report 
 * Florian 
 * 
 */

public class ReportFromDatabase implements ReportDatabaseInterface {
	private final static Logger logger = LoggerFactory.getLogger(ReportFromDatabase.class);
	private final static String HTR_MODULE = "CITlabHtrJob";
	private final static String OCR_MODULE = "FinereaderOcrJob";
	private final static String LA_MODULE = "CITlabAdvancedLaJobMultiThread";
	
	static int imagesUploaded = 0;
	static int docScanUploaded = 0;
	static int countJobs = 0;
	static int countNewUsers = 0;
	static int countUsers = 0;
	static String [] mostActiveUsers = new String[6];
	static String [] mostActiveDocScan = new String[6];
	static String [] mostActiveUsersSave = new String[6];
	static String [] trainingTime = new String[6];
	static String [] htrRunTime = new String[6];
	static String [] ocrRunTime = new String[6];
	static String [] laRunTime = new String[6];
	static String totalTrainingTime;
	static String totalHtrTime;
	static String totalOcrTime;
	static String totalLaTime;
	static String htrModelsCreated;
	static String runningTotals = "Running Totals : ";
	static int countActiveUsers = 0;
	static int countTotalSaves = 0;
	static int countCreatedDocs = 0;
	static int countDuplDocs = 0;
	static int countExpDocs = 0;
	static int countDelDocs = 0;
	static int countLayoutAnalysis = 0;
	static int countHTR = 0;
	static int countKWS = 0;
	static int countTags = 0;
	static Set <Integer> pageIndices = new HashSet<Integer>();
	static String format = "%s : %s | %s";
	static String format2 = "%s : %s";
	static String format3 = "%s ";
	

	public static java.sql.Date sqlTimeNow() {
		LocalDateTime now = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
		java.sql.Date sqlNow = java.sql.Date.valueOf(now.toLocalDate());
		return sqlNow;
	}

	public static java.sql.Date sqlTimeAgo(int timePeriodDays) {
		LocalDateTime timeAgo = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).minusDays(timePeriodDays);
		java.sql.Date sqlAgo = java.sql.Date.valueOf(timeAgo.toLocalDate());
		return sqlAgo;
	}

	public static void sendReportMail(File[] files, int timePeriod) {

		List<String> mailingList = ReportDatabaseInterface.mailingList();
		String reportTime = null;
		
		switch (timePeriod) {
			
		case 1: reportTime = "Daily";
				break;
		case 7: reportTime = "Weekly";
				break;
		case 30: reportTime = "Monthly";
				break;
		default: reportTime = "";
		
		}
		String trainingString = Arrays.stream(trainingTime).filter(s -> s != null).collect(Collectors.joining("\n"));
		String htrRunString = Arrays.stream(htrRunTime).filter(s -> s != null).collect(Collectors.joining("\n"));
		String ocrRunString = Arrays.stream(ocrRunTime).filter(s -> s != null).collect(Collectors.joining("\n"));
		String laString = Arrays.stream(laRunTime).filter(s -> s != null).collect(Collectors.joining("\n"));
		String activeUsersString = Arrays.stream(mostActiveUsers).filter(s -> s != null).collect(Collectors.joining("\n"));
		String activeSavesString = Arrays.stream(mostActiveUsersSave).filter(s -> s != null).collect(Collectors.joining("\n"));
		String activeDocScanString = Arrays.stream(mostActiveDocScan).filter(s -> s != null).collect(Collectors.joining("\n"));
		
		String messageText = "This is the latest report from " + sqlTimeNow()
				+ " with detailed user data over a period of "+timePeriod+" day(s) \n"
				+"\nNew Users : "+countNewUsers+" \n"
				+"Active Users / Unique Logins : "+ countActiveUsers+" \n"
				+"Jobs processed in total: "+countJobs+" \n"
				+"Created Documents: "+countCreatedDocs+ " \n"
				+"Duplicated Documents: "+countDuplDocs+ " \n"
				+"Export Documents: "+countExpDocs+ " \n"
				+"Deleted Documents: "+countDelDocs+ " \n"
				+"KWS searches: "+countKWS+ " \n"
				+"Layout Analysis Jobs: "+countLayoutAnalysis+ " \n"
				+"HTR Jobs : "+countHTR+ " \n"
				+"HTR Models created: "+htrModelsCreated+" \n"
				+"\nImage Uploads : "+imagesUploaded+"\n"+activeUsersString+"\n"
				+"\nDocScan Uploads : "+docScanUploaded+"\n"+activeDocScanString+"\n"
				+"\nSave Actions : "+countTotalSaves+" \n"+activeSavesString+"\n"
				+"\nTraining Runtime : "+totalTrainingTime+ " \n"+trainingString+"\n"
				+"\nHTR Runtime : "+totalHtrTime+ "\n"+htrRunString+"\n"
				+"\nOCR Runtime : "+totalOcrTime+ " \n"+ocrRunString+"\n"
				+"\nLA Runtime : "+totalLaTime+ " \n"+laString+"\n"
				+"\n"+runningTotals;
		
		
		
		for (String mailTo : mailingList) {

			try {

				MailUtils.TRANSKRIBUS_UIBK_MAIL_SERVER
						.sendMail(mailTo, reportTime+ " report from " + sqlTimeNow(),
								messageText,
								files, "", false, false);
			} catch (MessagingException e) {
				logger.error("Could not send mail to " + mailTo, e);
			}
		}

	}
	
	public static void emailMessage(Connection conn, int timePeriod) throws SQLException {
		
		UserDao dao = new UserDao();
		try {
			List<TrpUser> userList = dao.getUserByDate(sqlTimeAgo(timePeriod), true);
			countNewUsers = userList.size();
			List<TrpUser> totalUser = dao.getUserList(-1, -1, false, null, null);
			List<TrpUser> totalActiveUser = dao.getUserList(-1, -1, true, null, null);
			runningTotals += "\nTotal Users : "+totalUser.size()
							+"\nTotal Active Users (Activated account via registration link) : "+totalActiveUser.size();
		} catch (EntityNotFoundException | SQLException e) {
			logger.error(e.getMessage(), e);
		}
		
		String sqlLogins = "select count(distinct user_id) from actions where type_id = 2 and time between ? and ?";
		String sqlMostActive = "select distinct uploader, count (distinct images.image_id) from images join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join doc_md on PAGES.DOCID = DOC_MD.DOCID where created between ? and ? group by uploader order by count(distinct images.image_id) desc";
		String sqlMostSaves = "select distinct user_name, count (type_id) from actions where type_id = 1 and time between ? and ? group by user_name order by count (type_id) desc";
		String sqlLA = "select count(*) from jobs where (module_name like 'NcsrLaModule' or module_name like 'LaModule') and started between ? and ?";
		String sqlHTR = "select count(*) from jobs where module_name like 'CITlabHtrAppModule' and started between ? and ?";
		String sqlKWS = "select count(*) from jobs where module_name like 'KwsModule' and started between ? and ?";
		String sqlcreatedDoc = "select count(*) from jobs where type like 'Create Document' and started between ? and ?";
		String sqlDuplDoc = "select count(*) from jobs where type like 'Duplicate Document' and started between ? and ?";
		String sqlExpDoc = "select count(*) from jobs where type like 'Export Document' and started between ? and ?";
		String sqlDelDoc = "select count(*) from jobs where type like 'Delete Document' and started between ? and ?";
		String sqlHtrPages = "select pages,docid from jobs where module_name like 'CITlabHtrAppModule' and started between ? and ?";
		String sqlImages = "select count(*) from images join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join doc_md on PAGES.DOCID = DOC_MD.DOCID where images.created between ? and ?";
		String sqlJobs = "select count(*) from jobs where started between ? and ? order by started desc";
		String sqlSavesTotal = "select count(*) from actions where type_id = 1 and time between ? and ?";
		String sqlDocScanImages = "select count(*) from images \n" + 
				"join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join jobs on PAGES.DOCID = jobs.DOCID join session_history on jobs.SESSION_HISTORY_ID = session_history.SESSION_HISTORY_ID \n" + 
				"where session_history.USERAGENT like '%Android%' and images.CREATED between ? and ?";
		String sqlDocScanUsers = "select distinct jobs.USERID, count (distinct images.image_id) from images \n" + 
				"join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join jobs on PAGES.DOCID = jobs.DOCID join session_history on jobs.SESSION_HISTORY_ID = session_history.SESSION_HISTORY_ID \n" + 
				"where session_history.USERAGENT like '%Android%' and images.CREATED between ? and ? \n" + 
				"group by jobs.USERID order by count(distinct images.image_id) desc";
		
		PreparedStatement prep1 = conn.prepareStatement(sqlLogins);
		PreparedStatement prep2 = conn.prepareStatement(sqlMostActive);
		PreparedStatement prep3 = conn.prepareStatement(sqlMostSaves);
		PreparedStatement prep4 = conn.prepareStatement(sqlLA);
		PreparedStatement prep5 = conn.prepareStatement(sqlHTR);
		PreparedStatement prep6 = conn.prepareStatement(sqlKWS);
		PreparedStatement prep7 = conn.prepareStatement(sqlcreatedDoc);
		
		PreparedStatement prep8 = conn.prepareStatement(sqlDuplDoc);
		PreparedStatement prep9 = conn.prepareStatement(sqlExpDoc);
		PreparedStatement prep10 = conn.prepareStatement(sqlDelDoc);
		PreparedStatement prep11 = conn.prepareStatement(sqlHtrPages);
		PreparedStatement prep12 = conn.prepareStatement(sqlImages);
		PreparedStatement prep13 = conn.prepareStatement(sqlJobs);
		PreparedStatement prep14 = conn.prepareStatement(sqlSavesTotal);
		
		PreparedStatement prep15 = conn.prepareStatement(sqlDocScanImages);
		PreparedStatement prep16 = conn.prepareStatement(sqlDocScanUsers);
		
		prep1.setDate(1, sqlTimeAgo(timePeriod));
		prep1.setDate(2, sqlTimeNow());
		
		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());
		
		prep3.setDate(1, sqlTimeAgo(timePeriod));
		prep3.setDate(2, sqlTimeNow());
		
		prep4.setDate(1, sqlTimeAgo(timePeriod));
		prep4.setDate(2, sqlTimeNow());
		
		prep5.setDate(1, sqlTimeAgo(timePeriod));
		prep5.setDate(2, sqlTimeNow());
		
		prep6.setDate(1, sqlTimeAgo(timePeriod));
		prep6.setDate(2, sqlTimeNow());
		
		prep7.setDate(1, sqlTimeAgo(timePeriod));
		prep7.setDate(2, sqlTimeNow());
		
		prep8.setDate(1, sqlTimeAgo(timePeriod));
		prep8.setDate(2, sqlTimeNow());
		
		prep9.setDate(1, sqlTimeAgo(timePeriod));
		prep9.setDate(2, sqlTimeNow());
		
		prep10.setDate(1, sqlTimeAgo(timePeriod));
		prep10.setDate(2, sqlTimeNow());
		
		prep11.setDate(1, sqlTimeAgo(timePeriod));
		prep11.setDate(2, sqlTimeNow());
		
		prep12.setDate(1, sqlTimeAgo(timePeriod));
		prep12.setDate(2, sqlTimeNow());
		
		prep13.setDate(1, sqlTimeAgo(timePeriod));
		prep13.setDate(2, sqlTimeNow());
		
		prep14.setDate(1, sqlTimeAgo(timePeriod));
		prep14.setDate(2, sqlTimeNow());
		
		prep15.setDate(1, sqlTimeAgo(timePeriod));
		prep15.setDate(2, sqlTimeNow());
		
		prep16.setDate(1, sqlTimeAgo(timePeriod));
		prep16.setDate(2, sqlTimeNow());
		
		ResultSet rs1 = prep1.executeQuery();
		ResultSet rs2 = prep2.executeQuery();
		ResultSet rs3 = prep3.executeQuery();
		ResultSet rs4 = prep4.executeQuery();
		ResultSet rs5 = prep5.executeQuery();
		ResultSet rs6 = prep6.executeQuery();
		ResultSet rs7 = prep7.executeQuery();
		ResultSet rs8 = prep8.executeQuery();
		ResultSet rs9 = prep9.executeQuery();
		ResultSet rs10 = prep10.executeQuery();
		ResultSet rs11 = prep11.executeQuery();
		ResultSet rs12 = prep12.executeQuery();
		ResultSet rs13 = prep13.executeQuery();
		ResultSet rs14 = prep14.executeQuery();
		ResultSet rs15 = prep15.executeQuery();
		ResultSet rs16 = prep16.executeQuery();
		
		while (rs1.next() && rs4.next() && rs5.next() && rs6.next() && rs7.next() && rs8.next() && rs9.next() && rs10.next() && rs11.next() && rs12.next() && rs13.next() && rs14.next() && rs15.next()) {
			countActiveUsers = rs1.getInt("count(distinctuser_id)");
			countLayoutAnalysis = rs4.getInt("count(*)");
			countHTR = rs5.getInt("count(*)");
			countKWS = rs6.getInt("count(*)");
			countCreatedDocs = rs7.getInt("count(*)");
			countDuplDocs = rs8.getInt("count(*)");
			countExpDocs = rs9.getInt("count(*)");
			countDelDocs = rs10.getInt("count(*)");
			imagesUploaded = rs12.getInt("count(*)");
			docScanUploaded = rs15.getInt("count(*)");
			countJobs = rs13.getInt("count(*)");
			countTotalSaves = rs14.getInt("count(*)");
			if(rs11.getString("pages") != null){
				try {
					pageIndices = CoreUtils.parseRangeListStr(rs11.getString("pages"),rs11.getInt("docid"));
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
			
		}
		for(int i = 0; i <= 5; i++) {
			if(rs2.next()) {
				mostActiveUsers[i] = String.format(format2, rs2.getString("uploader"),rs2.getInt("count(distinctimages.image_id)"));
			}
			
			if(rs3.next()) {
				mostActiveUsersSave[i] = String.format(format2,rs3.getString("user_name"),rs3.getInt("count(type_id)"));
			}

			if(rs16.next()) {
				mostActiveDocScan[i] = String.format(format2,rs16.getString("userid"),rs16.getInt("count(distinctimages.image_id)"));
			}		
		}
		
		
	}
	
	public static void getJobTime(Connection conn, int timePeriod, String moduleName) throws SQLException {
		
		String SQL =	"select userid,sum(endtime - starttime),sum(total_work)\n" + 
						"from jobs\n" + 
						"where job_impl like ? and STATE like 'FINISHED' and started between ? and ?\n" + 
						"group by  userid\n" + 
						"order by sum(endtime - starttime) DESC\n" + 
						"FETCH FIRST 10 ROWS ONLY";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setString(1, moduleName);
		prep.setDate(2, sqlTimeAgo(timePeriod));
		prep.setDate(3, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		
		for(int i = 0; i < 5; i++) {
			rs.next();
			switch(moduleName) {
			case HTR_MODULE:
				htrRunTime[i] = String.format(format, rs.getString("userid")," "+hourFormat( rs.getLong("sum(endtime-starttime)")),"Pages : "+rs.getLong("sum(total_work)"));
				break;
			case OCR_MODULE:			
				ocrRunTime[i] = String.format(format2, rs.getString("userid")," "+hourFormat( rs.getLong("sum(endtime-starttime)")));
				break;
			case LA_MODULE:
				laRunTime[i] = String.format(format, rs.getString("userid")," "+hourFormat( rs.getLong("sum(endtime-starttime)")),"Pages : "+rs.getLong("sum(total_work)"));
			}
		
		}
	}
	
	public static void getTotalJobTime(Connection conn, int timePeriod, String moduleName) throws SQLException {
			
			String SQL =	"select sum(endtime - starttime),sum(total_work)\n" + 
							"from jobs\n" + 
							"where job_impl like ? and STATE like 'FINISHED' and started between ? and ?";
			PreparedStatement prep = conn.prepareStatement(SQL);
			prep.setString(1, moduleName);
			prep.setDate(2, sqlTimeAgo(timePeriod));
			prep.setDate(3, sqlTimeNow());
			ResultSet rs = prep.executeQuery();
		
			while(rs.next()) {
				switch(moduleName) {
				case HTR_MODULE:
					totalHtrTime = " "+hourFormat(rs.getLong("sum(endtime-starttime)"))+" | Pages : "+rs.getInt("sum(total_work)");
					break;
				case OCR_MODULE:
					totalOcrTime =" "+hourFormat(rs.getLong("sum(endtime-starttime)"));
					break;
				case LA_MODULE:
					totalLaTime= " "+hourFormat(rs.getLong("sum(endtime-starttime)"))+" | Pages : "+rs.getInt("sum(total_work)");
				}
			
			}
		}
	
	
	public static void getTrainingTime(Connection conn, int timePeriod) throws SQLException {
		
		String SQL = 	"select userid ,sum(endtime - starttime),sum(j.total_work)\n" + 
						"from jobs j \n" + 
						"where j.state like 'FINISHED' and started between ? and ? and  (j.JOB_IMPL like 'CITlabHtrTrainingJob' or j.JOB_IMPL like 'CITlabHtrPlusTrainingJob')\n" + 
						"group by  userid\n" + 
						"order by sum(endtime - starttime) DESC\n" + 
						"FETCH FIRST 10 ROWS ONLY";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		
		int i = 0;
		while(rs.next() && i <= 5) {
				trainingTime[i] =  String.format(format2, rs.getString("userid")," "+hourFormat( rs.getInt("sum(endtime-starttime)")));
				i++;
		}	
		
	}
	
	public static void getTrainingTotalTime(Connection conn, int timePeriod) throws SQLException {
			
			String SQL = 	"select sum(endtime - starttime),sum(total_work)\n" + 
							"from jobs j \n" + 
							"where j.state like 'FINISHED' and started between ? and ? and  (j.JOB_IMPL like 'CITlabHtrTrainingJob' or j.JOB_IMPL like 'CITlabHtrPlusTrainingJob')";
			PreparedStatement prep = conn.prepareStatement(SQL);
			prep.setDate(1, sqlTimeAgo(timePeriod));
			prep.setDate(2, sqlTimeNow());
			ResultSet rs = prep.executeQuery();
			while(rs.next()) {
				totalTrainingTime = " "+hourFormat(rs.getLong("sum(endtime-starttime)"));
			}
			
			
		}
	
	private static void getRunningTotals(Connection conn) throws SQLException {
		String SQL = 	"select count(htr_id) as models\n" + 
						"from htr";
		PreparedStatement prep = conn.prepareStatement(SQL);
		ResultSet rs = prep.executeQuery();
		while(rs.next()) {
			runningTotals += "\nTotal HTR Models : "+rs.getInt("models");
		}
		SQL = 	"select count(*)\n" + 
				"from images join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join doc_md on PAGES.DOCID = DOC_MD.DOCID";
		prep = conn.prepareStatement(SQL);
		rs = prep.executeQuery();
		while(rs.next()) {
			runningTotals += "\nTotal Images : "+rs.getInt("count(*)");
		}
		SQL = 	"select count(*) from images\n" + 
				"join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join jobs on PAGES.DOCID = jobs.DOCID join session_history on jobs.SESSION_HISTORY_ID = session_history.SESSION_HISTORY_ID\n" + 
				"where session_history.USERAGENT like '%Android%'";
		prep = conn.prepareStatement(SQL);
		rs = prep.executeQuery();
		while(rs.next()) {
			runningTotals += "\nTotal DocScan uploaded Images : "+rs.getInt("count(*)");
		}
		
	
	}
	
	
	public static void getHtrModelCreated(Connection conn, int timePeriod) throws SQLException {
		String SQL = 	"select count(htr_id), sum(nr_of_words)\n" + 
				"from htr \n" + 
				"where created between ? and ?";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		while(rs.next()) {
			htrModelsCreated =  ""+rs.getInt("count(htr_id)")+" | Words : "+rs.getInt("sum(nr_of_words)");
		}
	}

	public static void generateReport(int timePeriod) {


		DbConnection.setConfig(DbConfig.Prod);
		try (Connection conn = DbConnection.getConnection()) {

			
			getJobTime(conn, timePeriod, HTR_MODULE );
			
			getJobTime(conn, timePeriod, OCR_MODULE);
			
			getJobTime(conn, timePeriod, LA_MODULE);
			
			getTotalJobTime(conn, timePeriod, HTR_MODULE);
			
			getTotalJobTime(conn, timePeriod, OCR_MODULE);
			
			getTotalJobTime(conn, timePeriod, LA_MODULE);
			
			getTrainingTotalTime(conn,timePeriod);
			
			getHtrModelCreated(conn, timePeriod);
			
			getTrainingTime(conn, timePeriod);
			
			getRunningTotals(conn);
			
			emailMessage(conn,timePeriod);
			
			sendReportMail(null, timePeriod);


		}

		catch (SQLException e) {
			logger.error("A database operation failed.", e);

		}

	}


	private static String hourFormat(long l) {
		return String.format("%02d:%02d:%02d", 
			    TimeUnit.MILLISECONDS.toHours(l),
			    TimeUnit.MILLISECONDS.toMinutes(l) - 
			    TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(l)),
			    TimeUnit.MILLISECONDS.toSeconds(l) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(l)));
	}
	
	

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				DbConnection.shutDown();
			}
		});
		generateReport(Integer.parseInt(args[0]));
		Runtime.getRuntime().exit(0);
	}

}
