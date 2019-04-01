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
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.dea.fimgstoreclient.beans.ImgType;
import org.dea.fimgstoreclient.utils.FimgStoreUriBuilder;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import eu.transkribus.core.io.FimgStoreReadConnection;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.util.*;
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
	
	static int imagesUploaded = 0;
	static int countJobs = 0;
	static int countNewUsers = 0;
	static int countUsers = 0;
	static String [] mostActiveUsers = new String[6];
	static String [] mostActiveUsersSave = new String[6];
	static int countActiveUsers = 0;
	static int countCreatedDocs = 0;
	static int countDuplDocs = 0;
	static int countExpDocs = 0;
	static int countDelDocs = 0;
	static int countLayoutAnalysis = 0;
	static int countHTR = 0;
	static int countKWS = 0;
	static int countTags = 0;
	// TODO List count of tags & msot common tags
	static Set <Integer> pageIndices = new HashSet<Integer>();

	public static java.sql.Date sqlTimeNow() {
		LocalDateTime now = LocalDateTime.now();
		java.sql.Date sqlNow = java.sql.Date.valueOf(now.toLocalDate());
		return sqlNow;
	}

	public static java.sql.Date sqlTimeAgo(int timePeriodDays) {
		LocalDateTime timeAgo = LocalDateTime.now().minusDays(timePeriodDays);
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
				+ " including a PDF with charts and XLS with detailed user data \n"
				+"\nImages Uploaded : " +imagesUploaded+ "\n"
				+"New Users : "+countNewUsers+" \n"
				+"Active Users / Unique Logins : "+countActiveUsers+" \n"
				+"\nMost Active Users Image Uploads : \n\n"+mostActiveUsers[0]+" \n"+mostActiveUsers[1]+"\n"+mostActiveUsers[2]+"\n"+mostActiveUsers[3]+"\n"+mostActiveUsers[4]+"\n"
				+"\nMost Active Users Save Actions : \n\n"+mostActiveUsersSave[0]+" \n"+mostActiveUsersSave[1]+"\n"+mostActiveUsersSave[2]+"\n"+mostActiveUsersSave[3]+"\n"+mostActiveUsersSave[4]+"\n"
				+"\nJobs processed in total: "+countJobs+" \n"
				+"\nCount Created Documents: "+countCreatedDocs+ " \n"
				+"Count Duplicated Documents: "+countDuplDocs+ " \n"
				+"Count Export Documents: "+countExpDocs+ " \n"
				+"Count Deleted Documents: "+countDelDocs+ " \n"
				+"Count Layout Analysis Jobs: "+countLayoutAnalysis+ " \n"
				+"Count HTR Jobs : "+countHTR+ " \n" 
				+"Pages run with HTR : "+pageIndices.size()+" \n"
				+"Count KWS Jobs : "+countKWS+ " \n"
				+"Count Tags : "+countTags;
		
		for (String mailTo : mailingList) {

			try {

				MailUtils.TRANSKRIBUS_UIBK_MAIL_SERVER
						.sendMail(mailTo, reportTime+ " report from " + sqlTimeAgo(timePeriod)+ " to " + sqlTimeNow(),
								messageText,
								files, "", false, false);
			} catch (MessagingException e) {

				e.printStackTrace();
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
			e.printStackTrace();

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
			e.printStackTrace();
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
		String sqlTags = "select count(*) tags";

		
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
		
		while (rs1.next() && rs4.next() && rs5.next() && rs6.next() && rs7.next() && rs8.next() && rs9.next() && rs10.next() && rs11.next()) {
			countActiveUsers = rs1.getInt("count(distinctuser_id)");
			countLayoutAnalysis = rs4.getInt("count(*)");
			countHTR = rs5.getInt("count(*)");
			countKWS = rs6.getInt("count(*)");
			countCreatedDocs = rs7.getInt("count(*)");
			countDuplDocs = rs8.getInt("count(*)");
			countExpDocs = rs9.getInt("count(*)");
			countDelDocs = rs10.getInt("count(*)");
			if(rs11.getString("pages") != null){
				try {
					pageIndices = CoreUtils.parseRangeListStr(rs11.getString("pages"),rs11.getInt("docid"));
				} catch (IOException e) {
					e.printStackTrace();
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

	public static void failedJobsChart(Connection conn, int timePeriod) throws SQLException {

		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		DefaultPieDataset piedataset = new DefaultPieDataset();

		String sqlFailed = "select count(*) from jobs where STATE like 'FAILED' AND DESCRIPTION not like 'Could not create workdir%' AND DESCRIPTION not like '%Auf dem Gerät ist kein Speicherplatz mehr verfügbar%'  AND STARTED between ? and ?";
		String sqlDone = "select count(*) from jobs where STATE not like 'FAILED' AND DESCRIPTION not like 'Could not create workdir%' AND DESCRIPTION not like '%Auf dem Gerät ist kein Speicherplatz mehr verfügbar%'   AND STARTED between ? and ?";

		PreparedStatement prep = conn.prepareStatement(sqlFailed);
		PreparedStatement prep2 = conn.prepareStatement(sqlDone);

		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());

		ResultSet rs = prep.executeQuery();
		ResultSet rs2 = prep2.executeQuery();

		while (rs.next() && rs2.next()) {
			int countFailed = rs.getInt("count(*)");
			int countDone = rs2.getInt("count(*)");
			countJobs = countFailed + countDone;
			dataset.addValue(countFailed, "Jobs failed", " ");
			dataset.addValue(countDone, "Jobs done", " ");
			piedataset.setValue("Jobs done", countDone);
			piedataset.setValue("Jobs failed", countFailed);

		}

		JFreeChart pieChart = ChartFactory.createPieChart("Pie Chart failed jobs", piedataset, true, true, false);

		JFreeChart barChart = ChartFactory.createBarChart(
				"All jobs failed/done between " + sqlTimeAgo(timePeriod) + " and " + sqlTimeNow(), " ",
				"Number of jobs", dataset, PlotOrientation.VERTICAL, true, true, false);

		int width = 640;
		int height = 480;
		float quality = 1;

		File BarChart = new File("images/FailedJobs.jpg");
		File PieChart = new File("images/FailedJobsPie.jpg");
		try {
			ChartUtilities.saveChartAsJPEG(BarChart, quality, barChart, width, height);
			ChartUtilities.saveChartAsJPEG(PieChart, quality, pieChart, width, height);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void allActionsChart(Connection conn, int timePeriod) throws SQLException {

		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String sqlSave = "select count(*) from actions where type_id = 1 and time between ? and ?";
		String sqlLogin = "select count(*) from actions where type_id = 2 and time between ? and ?";
		String sqlStatus = "select count(*) from actions where type_id = 3 and time between ? and ?";
		String sqlAcessDoc = "select count(*) from actions where type_id = 4 and time between ? and ?";

		PreparedStatement prep1 = conn.prepareStatement(sqlSave);
		PreparedStatement prep2 = conn.prepareStatement(sqlLogin);
		PreparedStatement prep3 = conn.prepareStatement(sqlStatus);
		PreparedStatement prep4 = conn.prepareStatement(sqlAcessDoc);

		prep1.setDate(1, sqlTimeAgo(timePeriod));
		prep1.setDate(2, sqlTimeNow());
		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());
		prep3.setDate(1, sqlTimeAgo(timePeriod));
		prep3.setDate(2, sqlTimeNow());
		prep4.setDate(1, sqlTimeAgo(timePeriod));
		prep4.setDate(2, sqlTimeNow());

		ResultSet rs1 = prep1.executeQuery();
		ResultSet rs2 = prep2.executeQuery();
		ResultSet rs3 = prep3.executeQuery();
		ResultSet rs4 = prep4.executeQuery();

		while (rs1.next() && rs2.next() && rs3.next() && rs4.next()) {

			int countSave = rs1.getInt("count(*)");
			int countLogin = rs2.getInt("count(*)");
			int countStatus = rs3.getInt("count(*)");
			int countAcess = rs4.getInt("count(*)");
			dataset.addValue(countSave, "Save Action", " ");
			dataset.addValue(countLogin, "Login Action", " ");
			dataset.addValue(countStatus, "Status Action", " ");
			dataset.addValue(countAcess, "Access Action", " ");

		}

		JFreeChart barChart = ChartFactory.createBarChart(
				"All actions between " + sqlTimeAgo(timePeriod) + " and " + sqlTimeNow(), " ", "Number of actions",
				dataset, PlotOrientation.VERTICAL, true, true, false);

		int width = 640;
		int height = 480;
		float quality = 1;

		File BarChart = new File("images/AllActions.jpg");
		try {
			ChartUtilities.saveChartAsJPEG(BarChart, quality, barChart, width, height);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void numberWordsTrainedChart(Connection conn, int timePeriod) throws SQLException {

		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String sql = "select sum(nr_of_words) from HTR where created between ? and ?";
		String sql2 = "select sum(nr_of_lines) from HTR where created between ? and ?";

		PreparedStatement prep = conn.prepareStatement(sql);
		PreparedStatement prep2 = conn.prepareStatement(sql2);

		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());

		ResultSet rs = prep.executeQuery();
		ResultSet rs2 = prep2.executeQuery();

		while (rs.next() && rs2.next()) {

			int countTrainedWords = rs.getInt("sum(nr_of_words)");
			int countTrainedLines = rs2.getInt("sum(nr_of_lines)");
			dataset.addValue(countTrainedWords, "Words trained", " ");
			dataset.addValue(countTrainedLines, "Lines trained", " ");

		}
		JFreeChart barChart = ChartFactory.createBarChart(
				"All words trained between " + sqlTimeAgo(timePeriod) + " and " + sqlTimeNow(), " ",
				"Number of words trained", dataset, PlotOrientation.VERTICAL, true, true, false);

		int width = 640;
		int height = 480;
		float quality = 1;

		File BarChart = new File("images/WordsTrained.jpg");
		try {
			ChartUtilities.saveChartAsJPEG(BarChart, quality, barChart, width, height);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void averageUsers(Connection conn, int timePeriod) throws SQLException {

		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String sqlTranskribusX = "select count(*) from actions a join session_history sh on a.session_history_id = sh.session_history_id where a.type_id = 2 AND sh.useragent like 'Jersey%' AND time between ? and ?";
		String sqlallLogins = "select count(*) from actions a join session_history sh on a.session_history_id = sh.session_history_id where a.type_id = 2  AND time between ? and ?";

		PreparedStatement prep = conn.prepareStatement(sqlTranskribusX);
		PreparedStatement prep2 = conn.prepareStatement(sqlallLogins);

		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());

		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());

		ResultSet rs = prep.executeQuery();
		ResultSet rs2 = prep2.executeQuery();

		while (rs.next() && rs2.next()) {
			int transLogins = rs.getInt("count(*)");
			int allLogins = rs2.getInt("count(*)");
			dataset.addValue(transLogins / 30, "TransktibusX Average", " ");
			dataset.addValue(allLogins / 30, "All Logins Average", " ");

		}
		JFreeChart barChart = ChartFactory.createBarChart(
				"Average unique user logins per day between " + sqlTimeAgo(timePeriod) + " and " + sqlTimeNow(), " ",
				"Average logins", dataset, PlotOrientation.VERTICAL, true, true, false);

		int width = 640;
		int height = 480;
		float quality = 1;

		File BarChart = new File("images/AverageUsers.jpg");
		try {
			ChartUtilities.saveChartAsJPEG(BarChart, quality, barChart, width, height);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void nrLoginsActionsExcel(Connection conn, int timePeriod) throws SQLException {

		FimgStoreUriBuilder uriBuilder = FimgStoreReadConnection.getUriBuilder();

		String sql = "select * from actions a join session_history sh on a.session_history_id = sh.session_history_id where a.type_id = 2 AND a.client_Id is null  AND time between ? and ? order by time desc";
		String sql2 = "select * from images join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join doc_md on PAGES.DOCID = DOC_MD.DOCID where tags_stored between ? and ? ";
		String sql3 = "select * from jobs where started between ? and ? order by started desc";

		HSSFWorkbook workbook = new HSSFWorkbook();
		HSSFSheet sheet = workbook.createSheet("Actions of Users");
		HSSFSheet sheet2 = workbook.createSheet("Images Uploaded");
		HSSFSheet sheet3 = workbook.createSheet("Jobs created");

		Map<String, Object[]> excelData = new HashMap<String, Object[]>();
		Map<String, Object[]> excelData2 = new HashMap<String, Object[]>();
		Map<String, Object[]> excelData3 = new HashMap<String, Object[]>();
		
		int rowCount1 = 0;
		int rowCount2 = 0;
		int rowCount3 = 0;

		PreparedStatement prep = conn.prepareStatement(sql);
		PreparedStatement prep2 = conn.prepareStatement(sql2);
		PreparedStatement prep3 = conn.prepareStatement(sql3);

		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());
		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());
		prep3.setDate(1, sqlTimeAgo(timePeriod));
		prep3.setDate(2, sqlTimeNow());

		ResultSet rs = prep.executeQuery();
		ResultSet rs2 = prep2.executeQuery();
		ResultSet rs3 = prep3.executeQuery();

		while (rs.next()) {

			rowCount1 = rowCount1 + 1;
			int actionId = rs.getInt("action_id");
			String userLogin = rs.getString("user_name");
			String userAgent = rs.getString("useragent");
			String ip = rs.getString("ip");
			String created = rs.getString("created");
			String destroyed = rs.getString("destroyed");
			String guiVersion = rs.getString("gui_version");

			excelData.put(Integer.toString(rowCount1),
					new Object[] { actionId, userLogin, userAgent, ip, created, destroyed, guiVersion });
			

		}
		while(rs2.next()) {
			
			rowCount2 = rowCount2 + 1;
			
			int imageId = rs2.getInt("docid");
			String imageKey = rs2.getString("imagekey");
			String uri = uriBuilder.getImgUri(imageKey, ImgType.view).toString();
			String imageFile = rs2.getString("imgfilename");
			String uploader = rs2.getString("uploader");
			String title = rs2.getString("title");
			String author = rs2.getString("author");
			String created2 = rs2.getString("tags_stored");
			
			excelData2.put(Integer.toString(rowCount2),
					new Object[] { imageId, uri, imageFile, uploader, title, author, created2 });
			
			
		}
		while(rs3.next()) {
			
			rowCount3 = rowCount3 + 1;
			
			int jobid = rs3.getInt("jobid");
			String userid = rs3.getString("userid");
			String type = rs3.getString("type");
			String description = rs3.getString("description");
			String pages = rs3.getString("pages");
			String module = rs3.getString("module_name");
			String started = rs3.getString("started");
			String ended = rs3.getString("ended");
			
			excelData3.put(Integer.toString(rowCount3),
					new Object[] { jobid, userid, type, description, pages, module, started, ended });
		}
		
		// load Data into worksheet

		Set<String> keyset = excelData.keySet();
		int rownum = 0;
		for (String key : keyset) {
			Row row = sheet.createRow(rownum++);
			Object[] objArr = excelData.get(key);
			int cellnum = 0;
			for (Object obj : objArr) {
				Cell cell = row.createCell(cellnum++);
				if (obj instanceof Integer) {
					cell.setCellValue((Integer) obj);
				} else {
					cell.setCellValue((String) obj);
				}
			}
		}

		Set<String> keyset2 = excelData2.keySet();
		int rownum2 = 0;
		try {
			for (String key : keyset2) {
				Row row2 = sheet2.createRow(rownum2++);
				Object[] objArr = excelData2.get(key);
				int cellnum = 0;
				for (Object obj : objArr) {
					Cell cell = row2.createCell(cellnum++);
					if (obj instanceof Integer) {
						cell.setCellValue((Integer) obj);
					} else {
						cell.setCellValue((String) obj);
					}
				}
			}
		}catch(IllegalArgumentException e) {
			logger.debug("Excel generation failed due to too many rows");
		}

		Set<String> keyset3 = excelData3.keySet();
		int rownum3 = 0;
		for (String key : keyset3) {
			Row row3 = sheet3.createRow(rownum3++);
			Object[] objArr = excelData3.get(key);
			int cellnum = 0;
			for (Object obj : objArr) {
				Cell cell = row3.createCell(cellnum++);
				if (obj instanceof Integer) {
					cell.setCellValue((Integer) obj);
				} else {
					cell.setCellValue((String) obj);
				}
			}
		}

		try {
			FileOutputStream file = new FileOutputStream(new File("report/Report_" + sqlTimeNow() + ".xls"));
			workbook.write(file);
			file.close();
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void countsCombinedTable(Connection conn, int timePeriod) throws SQLException {

		DefaultTableModel model = new DefaultTableModel(new String[] { "Images uploaded", "Jobs created ",
				"HTR Models created", "Login TranskribusX", "Saved Documents" }, 0);

		String sqlImages = "select count(*) from images join pages on IMAGES.IMAGE_ID = PAGES.IMAGE_ID join doc_md on PAGES.DOCID = DOC_MD.DOCID where images.created between ? and ?";
		String sqlJobs = "select count(*) from jobs where started between ? and ? order by started desc";
		String sqlHTR = "select count(*) from HTR where created between ? and ?";
		String sqlLogin = "select count(*) from actions a join session_history sh on a.session_history_id = sh.session_history_id where a.type_id = 2 AND sh.useragent like 'Jersey%' AND time between ? and ?";
		String sqlSaved = "select count(*) from actions where type_id = 1 and time between ? and ?";

		PreparedStatement prep = conn.prepareStatement(sqlImages);
		PreparedStatement prep2 = conn.prepareStatement(sqlJobs);
		PreparedStatement prep3 = conn.prepareStatement(sqlHTR);
		PreparedStatement prep4 = conn.prepareStatement(sqlLogin);
		PreparedStatement prep5 = conn.prepareStatement(sqlSaved);

		prep.setDate(1, sqlTimeAgo(timePeriod));
		prep.setDate(2, sqlTimeNow());

		prep2.setDate(1, sqlTimeAgo(timePeriod));
		prep2.setDate(2, sqlTimeNow());

		prep3.setDate(1, sqlTimeAgo(timePeriod));
		prep3.setDate(2, sqlTimeNow());

		prep4.setDate(1, sqlTimeAgo(timePeriod));
		prep4.setDate(2, sqlTimeNow());

		prep5.setDate(1, sqlTimeAgo(timePeriod));
		prep5.setDate(2, sqlTimeNow());

		ResultSet rs = prep.executeQuery();
		ResultSet rs2 = prep2.executeQuery();
		ResultSet rs3 = prep3.executeQuery();
		ResultSet rs4 = prep4.executeQuery();
		ResultSet rs5 = prep5.executeQuery();

		while (rs.next() && rs2.next() && rs3.next() && rs4.next() && rs5.next()) {
			imagesUploaded = rs.getInt("count(*)");
			countJobs = rs2.getInt("count(*)");
			int countHTR = rs3.getInt("count(*)");
			int countLogin = rs4.getInt("count(*)");
			int countSaved = rs5.getInt("count(*)");

			model.addRow(new Object[] { imagesUploaded, countJobs, countHTR, countLogin, countSaved });

		}

		JTable table = new JTable();
		table.setModel(model);
		JTableHeader header = table.getTableHeader();
		table.setSize(table.getPreferredSize());
		header.setSize(header.getPreferredSize());

		BufferedImage img = createImage(table);

		File outputfile = new File("images/CountsCombinedTable.jpg");
		try {
			ImageIO.write(img, "jpg", outputfile);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void generateReport(int timePeriod) {

		Statement stmt = null;

		DbConnection.setConfig(DbConfig.Prod);
		try (Connection conn = DbConnection.getConnection()) {

			stmt = conn.createStatement();

			failedJobsChart(conn, timePeriod);

			numberWordsTrainedChart(conn, timePeriod);

			averageUsers(conn, timePeriod);

			countsCombinedTable(conn, timePeriod);

			allActionsChart(conn, timePeriod);

			nrLoginsActionsExcel(conn, timePeriod);
			
			emailMessage(conn,timePeriod);

			covertJpgToPdf(timePeriod);

		}

		catch (SQLException e) {
			e.printStackTrace();

		}

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
