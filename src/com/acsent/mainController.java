package com.acsent;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import java.io.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

@SuppressWarnings({"UnusedParameters", "WeakerAccess"})
public class mainController implements Initializable {

    @FXML
    TextField dirText;
    @FXML
    GridPane gridPane;

    @FXML
    TableView<TableRow> filesTableView;
    @FXML
    TableColumn<TableRow, String> tableDirName;
    @FXML
    TableColumn<TableRow, String> tableFileName;
    @FXML
    TableColumn<TableRow, Long> tableFileSize;
    @FXML
    TableColumn<TableRow, String> tableStatus;
    @FXML
    TableColumn<TableRow, Integer> tableQty;

    @FXML
    TextField connectionStringText;

    @FXML
    Button findFilesButton;
    @FXML
    Button clearTableButton;
    @FXML
    Button processButton;
    @FXML
    Label messageLabel;

    private Stage stage;

    private final ObservableList<TableRow> data = FXCollections.observableArrayList();
    private HashMap<String, TableRow> rowsByFile;

    public class TableRow {
        private final SimpleStringProperty dirName;
        private final SimpleStringProperty fileName;
        private final SimpleLongProperty fileSize;
        private final SimpleStringProperty status;
        private final SimpleIntegerProperty qty;

        private TableRow() {
            this.dirName = new SimpleStringProperty(null);
            this.fileName = new SimpleStringProperty(null);
            this.fileSize = new SimpleLongProperty(0L);
            this.status = new SimpleStringProperty(null);
            this.qty = new SimpleIntegerProperty(0);
        }

        private TableRow(File file, Long fileSize) {

            this.dirName = new SimpleStringProperty(file.getParent());
            this.fileName = new SimpleStringProperty(file.getName());

            fileSize = fileSize / (1024 * 1024);
            this.fileSize = new SimpleLongProperty(fileSize);
            this.status = new SimpleStringProperty("");
            this.qty = new SimpleIntegerProperty(0);
        }

        public String getDirName() {
            return dirName.get();
        }

        public void setDirName(String value) {
            dirName.set(value);
        }

        public String getFileName() {
            return fileName.get();
        }

        public void setFileName(String value) {
            fileName.set(value);
        }

        public Long getFileSize() {
            return fileSize.get();
        }

        public void setFileSize(Long value) {
            fileSize.set(value);
        }

        public String getStatus() {
            return status.get();
        }

        public void setStatus(String value) {
            status.set(value);
        }

        public void setQty(Integer value) {
            qty.set(value);
        }

        public Integer getQty() {
            return qty.get();
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(this::stageOnClose);
    }

    public void initialize(URL url, ResourceBundle resourceBundle) {

        messageLabel.setText("");

        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        dirText.setText(prefs.get("Directory", ""));

        connectionStringText.setText(prefs.get("ConnectionString", ""));

        // Привязка таблицы к данным
        tableDirName.setCellValueFactory(new PropertyValueFactory<>("dirName"));
        tableFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        tableFileSize.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        tableStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        tableQty.setCellValueFactory(new PropertyValueFactory<>("qty"));

        filesTableView.setItems(data);

    }

    public void stageOnClose(WindowEvent windowEvent) {

        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        prefs.put("Directory", dirText.getText());
        prefs.put("ConnectionString", connectionStringText.getText());
    }

    public void dirButtonOnAction(ActionEvent actionEvent) {

        DirectoryChooser directoryChooser = new DirectoryChooser();

        File initialDirectory = new File(dirText.getText());
        if (initialDirectory.exists()) {
            directoryChooser.setInitialDirectory(initialDirectory);
        }

        File selectedDir = directoryChooser.showDialog(stage);

        if (selectedDir != null) {
            dirText.setText(selectedDir.getPath());
        }
    }

    public void findFilesButtonOnAction(ActionEvent actionEvent) {

        messageLabel.setText("");
        data.clear();

        File rootFolder = new File(dirText.getText());
        if (!rootFolder.exists()) {
            messageLabel.setText("Каталог не найден!");
            return;
        }

        String[] folders = rootFolder.list((folder, name) -> name.startsWith("rphost"));

        for (String folderName : folders) {

            File curFolder = new File(rootFolder + "//" + folderName);
            String[] filesInFolder = curFolder.list((folder, name) -> name.endsWith(".log"));

            for (String fileName : filesInFolder) {

                File tmpFile = new File(curFolder + "//" + fileName);
                Long size = tmpFile.length();

                if (size > 0) {
                    TableRow row = new TableRow(tmpFile, size);
                    data.add(row);
                }
            }

        }

    }

    public void clearTableButtonOnAction(ActionEvent actionEvent) {

        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        String tableName = prefs.get("Table", "logs");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText("Clear table '" + tableName + "'?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() != ButtonType.OK) {
            return;
        }

        DBTools.DriverType driverType= DBTools.DriverType.SQLite;
        String driverTypeAsString = prefs.get("dbDriver", "SQLite");
        if (driverTypeAsString.equals("MS SQL")) {
            driverType = DBTools.DriverType.MSSQL;
        }


        String serverName   = prefs.get("Server",   "");
        String databaseName = prefs.get("Database", "logs");
        String user         = prefs.get("user",     "");
        String password     = prefs.get("password", "");
        boolean is          = prefs.getBoolean("IntegratedSecurity", false);

        try {

            DBTools db = new DBTools(driverType);
            db.connect(serverName, databaseName, user, password, is);
            db.createTable(tableName);
            db.execute("DELETE FROM [" + tableName + "]");
            db.close();

            messageLabel.setText("Таблица очищена");

        } catch (Exception e) {
            showExceptionAlert(e);
        }

    }

    class UpdateTableThreadListener implements TJLoader.ThreadListener {

        public synchronized void setProgress(String fileName, int counter) {

            TableRow tableRow = rowsByFile.get(fileName);

            if (tableRow != null) {

                if (counter != 0) {
                    tableRow.setQty(counter);
                    System.out.println(counter);
                }

                filesTableView.refresh();
                //filesTableView.getProperties().put(TableViewSkinBase.REFRESH, Boolean.TRUE);
            }
        }


        public synchronized void setStatus(String fileName, TJLoader.Status status) {

            TableRow tableRow = rowsByFile.get(fileName);

            if (tableRow != null) {

                if (status == TJLoader.Status.BEGIN) {
                    tableRow.setStatus("+");
                } else if (status == TJLoader.Status.DONE) {
                    tableRow.setStatus("V");
                    System.out.println("end");
                }

                filesTableView.refresh();
                //filesTableView.getProperties().put(TableViewSkinBase.REFRESH, Boolean.TRUE);
            }
        }

    }

    public void processButtonOnAction(ActionEvent actionEvent) throws Exception {

        Preferences prefs = Preferences.userNodeForPackage(Main.class);

        DBTools.DriverType driverType = DBTools.DriverType.SQLite;
        String driverTypeAsString = prefs.get("dbDriver", "SQLite");
        if (driverTypeAsString.equals("MS SQL")) {
            driverType = DBTools.DriverType.MSSQL;
        }

        String serverName = prefs.get("Server", "");
        String databaseName = prefs.get("Database", "logs");
        String tableName = prefs.get("Table", "logs");
        String user = prefs.get("user", "");
        String password = prefs.get("password", "");
        boolean is = prefs.getBoolean("IntegratedSecurity", false);

        int readersCount = prefs.getInt("ReadersCount", 1);
        int writersCount = prefs.getInt("WritersCount", 1);
        boolean oneReaderPerFile = prefs.getBoolean("OneReaderPerFile", false);

        try {

            DBTools db = new DBTools(driverType);

            db.connect(serverName, databaseName, user, password, is);
            db.createTable(tableName);
            db.close();

        } catch (Exception e) {
            showExceptionAlert(e);
            return;
        }

        UpdateTableThreadListener updateTableThreadListener = new UpdateTableThreadListener();

        TJLoader tjLoader = new TJLoader();

        if (driverType == DBTools.DriverType.SQLite) {
            tjLoader.readersCount = 1;
            tjLoader.writersCount = 1;
            tjLoader.oneReaderPerFile = false;
        } else {
            tjLoader.readersCount = readersCount;
            tjLoader.writersCount = writersCount;
            tjLoader.oneReaderPerFile = oneReaderPerFile;
        }
        tjLoader.addListener(updateTableThreadListener);

        tjLoader.driverType   = driverType;
        tjLoader.serverName   = serverName;
        tjLoader.databaseName = databaseName;
        tjLoader.tableName    = tableName;
        tjLoader.user         = user;
        tjLoader.password     = password;
        tjLoader.integratedSecurity = is;

        ArrayList<String> filesArrayList = new ArrayList<>();

        rowsByFile = new HashMap<>();
        for (TableRow tableRow : data) {
            String fileName = tableRow.getDirName() + "\\" + tableRow.getFileName();
            rowsByFile.put(fileName, tableRow);
            filesArrayList.add(fileName);
        }

        tjLoader.processAllFiles(filesArrayList);

    }

    public void optionsButtonOnAction(ActionEvent actionEvent) throws IOException {

        URL url = Main.class.getResource("/options.fxml");
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();

        Stage optionsStage = new Stage();

        optionsController controller = loader.getController();
        controller.setStage(optionsStage);

        Scene scene = new Scene(root);
        optionsStage.setScene(scene);
        optionsStage.setTitle("Options");
        optionsStage.showAndWait();

        if (controller.connectionString != null) {
            connectionStringText.setText(controller.connectionString);
        }
    }

    void showExceptionAlert(Exception ex) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Dialog");
        alert.setHeaderText("Couldn't connect to server");
        alert.setContentText(ex.toString());

        // Create expandable Exception.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        // Set expandable Exception into the dialog pane.
        alert.getDialogPane().setExpandableContent(expContent);

        alert.showAndWait();

    }

}


