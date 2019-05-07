package eu.transkribus.report;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

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
	static int countJobs = 0;
	static int countNewUsers = 0;
	static int countUsers = 0;
	static String [] mostActiveUsers = new String[6];
	static String [] mostActiveUsersSave = new String[6];
	static String [] trainingTime = new String[6];
	static String [] htrRunTime = new String[6];
	static String [] ocrRunTime = new String[6];
	static String [] laRunTime = new String[6];
	static String totalTrainingTime;
	static String totalHtrTime;
	static String totalOcrTime;
	static String totalLaTime;
	static int countActiveUsers = 0;
	static int countCreatedDocs = 0;
	static int countDuplDocs = 0;
	static int countExpDocs = 0;
	static int countDelDocs = 0;
	static int countLayoutAnalysis = 0;
	static int countHTR = 0;
	static int countKWS = 0;
	static int countTags = 0;
	static Set <Integer> pageIndices = new HashSet<Integer>();

	public static java.sql.Date sqlTimeNow() {
		LocalDateTime now = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
		logger.debug(now.toString());
		java.sql.Date sqlNow = java.sql.Date.valueOf(now.toLocalDate());
		return sqlNow;
	}

	public static java.sql.Date sqlTimeAgo(int timePeriodDays) {
		LocalDateTime timeAgo = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).minusDays(timePeriodDays);
		logger.debug(timeAgo.toString());
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
		
		}
		
		String messageText = "This is the latest report from " + sqlTimeNow()
				+ " with detailed user data \n"
				+"\nImages Uploaded : " +imagesUploaded+ "\n"
				+"New Users : "+countNewUsers+" \n"
				+"Active Users / Unique Logins : "+countActiveUsers+" \n"
				+"\nMost Active Users Image Uploads : \n\n"+mostActiveUsers[0]+" \n"+mostActiveUsers[1]+"\n"+mostActiveUsers[2]+"\n"+mostActiveUsers[3]+"\n"+mostActiveUsers[4]+"\n"
				+"\nMost Active Users Save Actions : \n\n"+mostActiveUsersSave[0]+" \n"+mostActiveUsersSave[1]+"\n"+mostActiveUsersSave[2]+"\n"+mostActiveUsersSave[3]+"\n"+mostActiveUsersSave[4]+"\n"
				+"\nMost Training Runtime : \nTOTAL : "+totalTrainingTime+ " \n"+trainingTime[0]+" \n"+trainingTime[1]+"\n"+trainingTime[2]+"\n"+trainingTime[3]+"\n"+trainingTime[4]+"\n"
				+"\nMost HTR Runtime : \nTOTAL : "+totalHtrTime+ " \n"+htrRunTime[0]+" \n"+htrRunTime[1]+"\n"+htrRunTime[2]+"\n"+htrRunTime[3]+"\n"+htrRunTime[4]+"\n"
				+"\nMost OCR Runtime : \nTOTAL : "+totalOcrTime+ " \n"+ocrRunTime[0]+" \n"+ocrRunTime[1]+"\n"+ocrRunTime[2]+"\n"+ocrRunTime[3]+"\n"+ocrRunTime[4]+"\n"
				+"\nMost LA Runtime : \nTOTAL : "+totalLaTime+ " \n"+laRunTime[0]+" \n"+laRunTime[1]+"\n"+laRunTime[2]+"\n"+laRunTime[3]+"\n"+laRunTime[4]+"\n"
				+"\nJobs processed in total: "+countJobs+" \n"
				+"\nCount Created Documents: "+countCreatedDocs+ " \n"
				+"Count Duplicated Documents: "+countDuplDocs+ " \n"
				+"Count Export Documents: "+countExpDocs+ " \n"
				+"Count Deleted Documents: "+countDelDocs+ " \n"
				+"Count Layout Analysis Jobs: "+countLayoutAnalysis+ " \n"
				+"Count HTR Jobs : "+countHTR+ " \n" ;
		
		for (String mailTo : mailingList) {

			try {

				MailUtils.TRANSKRIBUS_UIBK_MAIL_SERVER
						.sendMail(mailTo, reportTime+ " report from " + sqlTimeAgo(timePeriod)+ " to " + sqlTimeNow(),
								messageText,
								files, "", false, false);
			} catch (MessagingException e) {
				logger.error("Could not send mail to " + mailTo, e);
			}
		}

	}

	public static void covertJpgToPdf(int timePeriod) {

		try {
			Document document = new Document();
			File pdfFile = new File("report/report" + sqlTimeNow() + ".pdf");
			PdfWriter.getInstance(document, new FileOutputStream(pdfFile));

			document.open();

			Image failedJobs = Image.getInstance("images/FailedJobs.jpg");
			failedJobs.scalePercent(70);

			Image failedJobsPie = Image.getInstance("images/FailedJobsPie.jpg");
			failedJobsPie.scalePercent(70);
			
			Image wordsTrained = Image.getInstance("images/WordsTrained.jpg");
			wordsTrained.scalePercent(70);

			Image averageUsers = Image.getInstance("images/AverageUsers.jpg");
			averageUsers.scalePercent(70);

			Image countsCombinedTable = Image.getInstance("images/CountsCombinedTable.jpg");
			
			Image allActions = Image.getInstance("images/AllActions.jpg");
			allActions.scalePercent(70);

			document.add(new Chunk("Database report from " + sqlTimeAgo(timePeriod) + " to " + sqlTimeNow() + ""));
			document.add(Chunk.NEWLINE);
			document.add(averageUsers);
			document.add(wordsTrained);
			document.newPage();
			document.add(failedJobs);
			document.add(failedJobsPie);
			document.add(allActions);
			document.add(new Paragraph(
					"Combined counts of images uploaded,jobs created, HTR models, logins transkribusX and saved Documents"));
			document.add(Chunk.NEWLINE);
			document.add(countsCombinedTable);

			document.close();

			File xlsFile = new File("report/Report_" + sqlTimeNow().toString() + ".xls");

			sendReportMail(new File[] { pdfFile, xlsFile }, timePeriod);
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	public static BufferedImage createImage(JTable table) {

		JTableHeader tableHeaderComp = table.getTableHeader();
		int totalWidth = tableHeaderComp.getWidth() + table.getWidth();
		int totalHeight = tableHeaderComp.getHeight() + table.getHeight();
		BufferedImage tableImage = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2D = (Graphics2D) tableImage.getGraphics();
		tableHeaderComp.paint(g2D);
		g2D.translate(0, tableHeaderComp.getHeight());
		table.paint(g2D);
		return tableImage;

	}
	
	public static void emailMessage(Connection conn, int timePeriod) throws SQLException {
		
		UserDao dao = new UserDao();
		try {
			List<TrpUser> userList = dao.getUserByDate(sqlTimeAgo(timePeriod), true);
			countNewUsers = userList.size();
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
		
		while (rs1.next() && rs4.next() && rs5.next() && rs6.next() && rs7.next() && rs8.next() && rs9.next() && rs10.next() && rs11.next() && rs12.next() && rs13.next()) {
			countActiveUsers = rs1.getInt("count(distinctuser_id)");
			countLayoutAnalysis = rs4.getInt("count(*)");
			countHTR = rs5.getInt("count(*)");
			countKWS = rs6.getInt("count(*)");
			countCreatedDocs = rs7.getInt("count(*)");
			countDuplDocs = rs8.getInt("count(*)");
			countExpDocs = rs9.getInt("count(*)");
			countDelDocs = rs10.getInt("count(*)");
			imagesUploaded = rs12.getInt("count(*)");
			countJobs = rs13.getInt("count(*)");
			if(rs11.getString("pages") != null){
				try {
					pageIndices = CoreUtils.parseRangeListStr(rs11.getString("pages"),rs11.getInt("docid"));
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
			
		}
		for(int i = 0; i <= 5; i++) {
			rs2.next();
			rs3.next();
			mostActiveUsers[i] = "User: "+rs2.getString("uploader")+" Uploaded Images: "+rs2.getInt("count(distinctimages.image_id)");
			mostActiveUsersSave[i] = "User: "+rs3.getString("user_name")+" Save Actions: "+rs3.getInt("count(type_id)");
		}
		
		
	}
	
	public static void getJobTime(Connection conn, int timePeriod, String moduleName) throws SQLException {
		
		String SQL =	"select userid,sum(endtime - starttime)\n" + 
						"from jobs\n" + 
						"where job_impl like ? and STATE like 'FINISHED' and started between ? and ?\n" + 
						"group by  userid,job_impl\n" + 
						"order by sum(endtime - starttime) DESC\n" + 
						"FETCH FIRST 10 ROWS ONLY";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setString(1, moduleName);
		prep.setDate(2, sqlTimeAgo(timePeriod));
		prep.setDate(3, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		
		for(int i = 0; i <= 5; i++) {
			rs.next();
			switch(moduleName) {
			case HTR_MODULE:
				htrRunTime[i] = "User: "+rs.getString("userid")+" HTR Runtime: "+hourFormat( rs.getInt("sum(endtime-starttime)"));
				break;
			case OCR_MODULE:
				ocrRunTime[i] = "User: "+rs.getString("userid")+" OCR Runtime: "+hourFormat( rs.getInt("sum(endtime-starttime)"));
				break;
			case LA_MODULE:
				laRunTime[i] = "User: "+rs.getString("userid")+" LA Runtime: "+hourFormat( rs.getInt("sum(endtime-starttime)"));
			}
		
		}
	}
	
public static void getTotalJobTime(Connection conn, int timePeriod, String moduleName) throws SQLException {
		
		String SQL =	"select sum(endtime - starttime)\n" + 
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
				totalHtrTime = hourFormat(rs.getLong("sum(endtime-starttime)"));
				break;
			case OCR_MODULE:
				totalOcrTime = hourFormat(rs.getLong("sum(endtime-starttime)"));
				break;
			case LA_MODULE:
				totalLaTime= hourFormat(rs.getLong("sum(endtime-starttime)"));
			}
		
		}
	}
	
	
	public static void getTrainingTime(Connection conn, int timePeriod) throws SQLException {
		
		String SQL = 	"select userid ,sum(endtime - starttime)\n" + 
						"from jobs j \n" + 
						"join JOB_IMPL_REGISTRY jir\n" + 
						" on j.JOB_IMPL = jir.JOB_IMPL\n" + 
						"where j.state like 'FINISHED' and started between ? and ? and  (j.JOB_IMPL like 'CITlabHtrTrainingJob' or j.JOB_IMPL like 'CITlabHtrPlusTrainingJob')\n" + 
						"group by  userid\n" + 
						"order by sum(endtime - starttime) DESC\n" + 
						"FETCH FIRST 10 ROWS ONLY";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		for(int i = 0; i <= 5; i++) {
			rs.next();
			trainingTime[i] = "User: "+rs.getString("userid")+" Training Runtime: "+hourFormat( rs.getLong("sum(endtime-starttime)"));
			
		}
		
	}
	
public static void getTrainingTotalTime(Connection conn, int timePeriod) throws SQLException {
		
		String SQL = 	"select sum(endtime - starttime)\n" + 
						"from jobs j \n" + 
						"where j.state like 'FINISHED' and started between ? and ? and  (j.JOB_IMPL like 'CITlabHtrTrainingJob' or j.JOB_IMPL like 'CITlabHtrPlusTrainingJob')";
		PreparedStatement prep = conn.prepareStatement(SQL);
		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		ResultSet rs = prep.executeQuery();
		while(rs.next()) {
			totalTrainingTime = hourFormat(rs.getLong("sum(endtime-starttime)"));
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
			
			getTrainingTime(conn, timePeriod);
			
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
		new File("images").mkdir();
		new File("report").mkdir();
		generateReport(Integer.parseInt(args[0]));
		Runtime.getRuntime().exit(0);
	}

}
