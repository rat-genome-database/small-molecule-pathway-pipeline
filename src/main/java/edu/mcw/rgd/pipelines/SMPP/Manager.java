package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.pipelines.PipelineManager;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.Date;

/**
 * Created by mtutaj on 10/27/2015.
 */
public class Manager {

    private String version;
    private String inputFile;
    private QC qc;
    private FileParser parser;
    private String staleAnnotDeleteThreshold;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));

        System.out.println(manager.getVersion());

        manager.run();
    }

    void run() throws Exception {

        Date startTime = new Date();

        String inputFilePath = downloadInputFile();
        System.out.println("Processing file "+inputFilePath);
        parser.setInputFileName(inputFilePath);

        Dao dao = new Dao();
        qc.setDao(dao);

        PipelineManager manager = new PipelineManager();
        manager.addPipelineWorkgroup(parser, "FP", 1, 0);
        manager.addPipelineWorkgroup(qc, "QC", 8, 0);
        manager.addPipelineWorkgroup(new DataLoad(dao), "DL", 1, 0);

        // because we are doing annotation QC and loading in parallel thread, conflicts could happen
        // resulting in a try to insert duplicate annotations;
        // we do allow for up-to 100000 duplicate annotations to be resolved later
        manager.getSession().setAllowedExceptions(100000);

        // violations of unique key during inserts of annotations will be handled silently,
        // without writing anything to the logs
        manager.getSession().registerUserException(new String[]{
                "FULL_ANNOT_MULT_UC", "DataIntegrityViolationException", "SQLIntegrityConstraintViolationException"});

        manager.run();

        int staleAnnotCount = dao.deleteStaleAnnotations(startTime, qc.getCreatedBy(), getStaleAnnotDeleteThreshold());
        if( staleAnnotCount!=0 ) {
            manager.getSession().incrementCounter("ZL: annotations stale deleted", staleAnnotCount);
        }

        manager.getSession().dumpCounters();

        qc.generateReportForUnmatchingSmpdbIds();

        System.out.println("=== ELAPSED TIME: "+ Utils.formatElapsedTime(startTime.getTime(), System.currentTimeMillis())+" ===");
    }

    String downloadInputFile() throws Exception {

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getInputFile());
        downloader.setLocalFile("data/smpdb_proteins.csv.zip");
        downloader.setPrependDateStamp(true);
        return downloader.downloadNew();
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setQc(QC qc) {
        this.qc = qc;
    }

    public QC getQc() {
        return qc;
    }

    public void setParser(FileParser parser) {
        this.parser = parser;
    }

    public FileParser getParser() {
        return parser;
    }

    public void setStaleAnnotDeleteThreshold(String staleAnnotDeleteThreshold) {
        this.staleAnnotDeleteThreshold = staleAnnotDeleteThreshold;
    }

    public String getStaleAnnotDeleteThreshold() {
        return staleAnnotDeleteThreshold;
    }
}
