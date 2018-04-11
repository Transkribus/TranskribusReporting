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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.mail.MessagingException;
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

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import eu.transkribus.core.io.FimgStoreReadConnection;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.DbConnection.DbConfig;
import eu.transkribus.persistence.util.MailUtils;

/*
 * 
 * Mailing report 
 * Florian 
 * 
 */

public class ReportFromDatabase implements ReportDatabaseInterface {

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

	public static void sendReportMail(File[] files) {

		List<String> mailingList = ReportDatabaseInterface.mailingList();

		for (String mailTo : mailingList) {

			try {
				MailUtils.TRANSKRIBUS_UIBK_MAIL_SERVER
						.sendMailSSL(mailTo, "Report from " + sqlTimeNow(),
								"This is the latest report from " + sqlTimeNow()
										+ " including a PDF with charts and XLS with detailed user data",
								files, "", false);
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

			sendReportMail(new File[] { pdfFile, xlsFile });
			// sendReportMail(files);

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
		String sql2 = "select * from images,DOC_MD where created between ? and ? ";
		String sql3 = "select * from jobs where started between ? and ? order by started desc";

		HSSFWorkbook workbook = new HSSFWorkbook();
		HSSFSheet sheet = workbook.createSheet("Actions of Users");
		HSSFSheet sheet2 = workbook.createSheet("Images Uploaded");
		HSSFSheet sheet3 = workbook.createSheet("Jobs created");

		Map<String, Object[]> excelData = new HashMap<String, Object[]>();
		Map<String, Object[]> excelData2 = new HashMap<String, Object[]>();
		Map<String, Object[]> excelData3 = new HashMap<String, Object[]>();
		int rowCount = 0;

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

		while (rs.next() && rs2.next() && rs3.next()) {

			rowCount = rowCount + 1;
			int actionId = rs.getInt("action_id");
			String userLogin = rs.getString("user_name");
			String userAgent = rs.getString("useragent");
			String ip = rs.getString("ip");
			String created = rs.getString("created");
			String destroyed = rs.getString("destroyed");
			String guiVersion = rs.getString("gui_version");

			int imageId = rs2.getInt("image_id");
			String imageKey = rs2.getString("imagekey");
			String uri = uriBuilder.getImgUri(imageKey, ImgType.view).toString();
			String imageFile = rs2.getString("imgfilename");
			String uploader = rs2.getString("uploader");
			String title = rs2.getString("title");
			String author = rs2.getString("author");
			int width = rs2.getInt("width");
			int height = rs2.getInt("height");
			String created2 = rs2.getString("created");

			int jobid = rs3.getInt("jobid");
			String userid = rs3.getString("userid");
			String type = rs3.getString("type");
			String description = rs3.getString("description");
			String pages = rs3.getString("pages");
			String module = rs3.getString("module_name");
			String started = rs3.getString("started");
			String ended = rs3.getString("ended");

			excelData.put(Integer.toString(rowCount),
					new Object[] { actionId, userLogin, userAgent, ip, created, destroyed, guiVersion });
			excelData2.put(Integer.toString(rowCount),
					new Object[] { imageId, uri, imageFile, uploader, title, author, width, height, created2 });
			excelData3.put(Integer.toString(rowCount),
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
		for (String key : keyset2) {
			Row row = sheet2.createRow(rownum2++);
			Object[] objArr = excelData2.get(key);
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

		Set<String> keyset3 = excelData3.keySet();
		int rownum3 = 0;
		for (String key : keyset3) {
			Row row = sheet3.createRow(rownum3++);
			Object[] objArr = excelData3.get(key);
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

		String sqlImages = "select count(*) from pages where docid in (select docid from jobs where  TYPE = 'Create Document' AND STARTED between ? and ?)";
		String sqlJobs = "select count(*) from jobs where docid in (select docid from jobs where  TYPE = 'Create Document' AND STARTED between ? and  ?)";
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
			int countImage = rs.getInt("count(*)");
			int countJobs = rs2.getInt("count(*)");
			int countHTR = rs3.getInt("count(*)");
			int countLogin = rs4.getInt("count(*)");
			int countSaved = rs5.getInt("count(*)");

			model.addRow(new Object[] { countImage, countJobs, countHTR, countLogin, countSaved });

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
