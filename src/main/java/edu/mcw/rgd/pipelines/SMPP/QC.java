package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.pipelines.PipelineRecord;
import edu.mcw.rgd.pipelines.RecordProcessor;
import edu.mcw.rgd.process.Utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by mtutaj on 10/27/2015.
 * <p>
 * QC code, run in multiple threads for performance reasons, to match incoming data
 * against data in RGD
 */
public class QC extends RecordProcessor {

    private Map<String,List<Term>> smpdbIdsToPathways = new TreeMap<>();
    private Map<String,String> smpdbIdsToPathwayName = new HashMap<>();
    static private final Map<String,String> smpdbIdsUnmatchable = new ConcurrentHashMap<>();

    private Dao dao;
    private String unmatchingSmpdbIdsReportFile;
    private int createdBy;
    private int refRgdId;
    private String dataSrc;
    private String nonmatchableSmpdbIdsFile;

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    /** load unmatchable ids from the properties file; line format:
     * <pre>
     SMP00004 - glycine and serine metabolism
     SMP00008 - phenylalanine and tyrosine metabolism
     * </pre>
     */
    @Override
    public void onInit() throws Exception {
        super.onInit();

        synchronized(smpdbIdsUnmatchable) {
            if( !smpdbIdsUnmatchable.isEmpty() ) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(getNonmatchableSmpdbIdsFile()))) {

                // separators for -dash' surrounded by spaces
                String[] separators = {" - ", " \u2010 "};

                String line;
                while ((line = reader.readLine()) != null) {

                    // skip empty lines
                    if( Utils.isStringEmpty(line.trim()) ) {
                        continue;
                    }

                    int splitPos = -1;
                    for (String sep : separators) {
                        splitPos = line.indexOf(sep);
                        if (splitPos >= 0)
                            break;
                    }

                    if (splitPos >= 0) {
                        String smpdbId = "SMP:" + line.substring(3, splitPos).trim();
                        String smpdbName = line.substring(splitPos + 3).trim();
                        smpdbIdsUnmatchable.put(smpdbId, smpdbName);
                    } else {
                        System.out.println("*** unexpected format in file " + getNonmatchableSmpdbIdsFile());
                    }
                }
                reader.close();
            }
            System.out.println("loaded unmatchable SMPDB ids: " + smpdbIdsUnmatchable.size());
            getSession().incrementCounter("QC: SMPDB IDs unmatchable", smpdbIdsUnmatchable.size());
        }
    }

    @Override
    public void process(PipelineRecord pipelineRecord) throws Exception {

        Record rec = (Record) pipelineRecord;

        rec.qcInit();

        qcPathwayTerms(rec);
        qcGenes(rec);
        if( rec.getMatchingPathwayTerms()!=null ) {
            qcOrthologs(rec);
            qcAnnots(rec);
        }
    }


    void qcAnnots(Record rec) throws Exception {

        // determine incoming annots
        if( rec.getHumanGene()==null ) {
            return;
        }
        String withInfo = "RGD:"+rec.getHumanGene().getRgdId();

        List<Annotation> annots = new ArrayList<>();
        for( Term term: rec.getMatchingPathwayTerms() ) {
            annots.add( createAnnotation(rec.getHumanGene(), null, rec.getSMPDbId(), term) );
            for( Gene gene: rec.getMouseGenes() ) {
                annots.add( createAnnotation(gene, withInfo, rec.getSMPDbId(), term) );
            }
            for( Gene gene: rec.getRatGenes() ) {
                annots.add( createAnnotation(gene, withInfo, rec.getSMPDbId(), term) );
            }
        }

        // qc incoming annots against RGD
        for( Annotation ann: annots ) {
            int annotKey = dao.getAnnotationKey(ann);
            if( annotKey==0 ) {
                rec.getForInsertAnnots().add(ann);
            } else {
                ann.setKey(annotKey);
                rec.getForUpdateAnnots().add(ann);
            }
        }
    }

    Annotation createAnnotation(Gene gene, String withInfo, String smpdbId, Term term) {
        if( gene!=null ) {
            Annotation ann = new Annotation();
            ann.setAnnotatedObjectRgdId(gene.getRgdId());
            ann.setCreatedBy(getCreatedBy());
            ann.setCreatedDate(new Date());
            ann.setDataSrc(getDataSrc());
            ann.setEvidence(gene.getSpeciesTypeKey()==SpeciesType.HUMAN ? "EXP" : "ISO");
            ann.setLastModifiedBy(getCreatedBy());
            ann.setLastModifiedDate(ann.getCreatedDate());
            ann.setXrefSource(smpdbId);
            ann.setObjectName(gene.getName());
            ann.setObjectSymbol(gene.getSymbol());
            ann.setRefRgdId(getRefRgdId());
            ann.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
            ann.setAspect("W");
            ann.setTermAcc(term.getAccId());
            ann.setTerm(term.getTerm());
            ann.setWithInfo(withInfo);
            ann.setSpeciesTypeKey(gene.getSpeciesTypeKey());
            return ann;
        } else {
            return null;
        }
    }

    synchronized void qcPathwayTerms(Record rec) throws Exception {

        if( smpdbIdsUnmatchable.containsKey(rec.getSMPDbId()) ) {
            return;
        }

        List<Term> pathwayTerms = smpdbIdsToPathways.get(rec.getSMPDbId());
        if( pathwayTerms==null ) {
            pathwayTerms = dao.getPathwaysForSmpdbId(rec.getSMPDbId());
            smpdbIdsToPathways.put(rec.getSMPDbId(), pathwayTerms);
            smpdbIdsToPathwayName.put(rec.getSMPDbId(), rec.getPathwayName());

            if( pathwayTerms.isEmpty() ) {
                getSession().incrementCounter("QC: SMPDB IDs without pathway match in RGD", 1);
            } else {
                getSession().incrementCounter("QC: SMPDB IDs with pathway match in RGD", 1);
            }
        }

        rec.setMatchingPathwayTerms(pathwayTerms);
    }

    void qcGenes(Record rec) throws Exception {

        if( rec.getMatchingPathwayTerms()==null ) {
            getSession().incrementCounter("QC: GENE MATCH0: SMPDB ID unmatchable", 1);
            return;
        }

        // try to match human gene by symbol
        if( !Utils.isStringEmpty(rec.getGeneSymbol()) ) {
            Gene humanGene = dao.getHumanGeneBySymbol(rec.getGeneSymbol());
            if (humanGene != null) {
                getSession().incrementCounter("QC: GENE MATCH1: by gene symbol", 1);

                rec.setHumanGene(humanGene);
                rec.setHumanGeneMatchType(Record.GENE_MATCH_TYPE.GENE_MATCH_BY_SYMBOL);
                return;
            }
        }

        // if no match try to match a gene by uniprot id
        if( !Utils.isStringEmpty(rec.getUniprotId()) ) {
            Gene humanGene = dao.getHumanGeneByUniprotId(rec.getUniprotId());
            if (humanGene != null) {
                getSession().incrementCounter("QC: GENE MATCH2: by UniProt Id", 1);

                rec.setHumanGene(humanGene);
                rec.setHumanGeneMatchType(Record.GENE_MATCH_TYPE.GENE_MATCH_BY_UNIPROT_ID);
                return;
            }
        }

        // if no match try to match a gene by uniprot id
        if( !Utils.isStringEmpty(rec.getGenBankId()) ) {
            Gene humanGene = dao.getHumanGeneByGenBankId(rec.getGenBankId());
            if (humanGene != null) {
                getSession().incrementCounter("QC: GENE MATCH3: by GenBank id", 1);

                rec.setHumanGene(humanGene);
                rec.setHumanGeneMatchType(Record.GENE_MATCH_TYPE.GENE_MATCH_BY_GENBANK_ID);
                return;
            }
        }

        // if no match try to match a gene by uniprot id
        if( !Utils.isStringEmpty(rec.getGeneSymbol()) ) {
            Gene humanGene = dao.getHumanGeneByAlias(rec.getGeneSymbol());
            if (humanGene != null) {
                getSession().incrementCounter("QC: GENE MATCH4: by gene symbol alias", 1);

                rec.setHumanGene(humanGene);
                rec.setHumanGeneMatchType(Record.GENE_MATCH_TYPE.GENE_MATCH_BY_ALIAS);
                return;
            }
        }

        getSession().incrementCounter("QC: GENE MATCH5: no match", 1);
        rec.setHumanGeneMatchType(Record.GENE_MATCH_TYPE.GENE_MATCH_NONE);

        //System.out.println("  NOMATCH: "+rec.getSMPDbId()+"|"+rec.getGeneSymbol()
        //        +"|"+rec.getUniprotId()+"|"+rec.getGenBankId());
    }

    void qcOrthologs(Record rec) throws Exception {

        // if there is a human gene, find rat and mouse orthologs
        if( rec.getHumanGene()==null ) {
            return;
        }

        for( Gene gene: dao.getOrthologs(rec.getHumanGene().getRgdId()) ) {
            if( gene.getSpeciesTypeKey()== SpeciesType.RAT ) {
                rec.getRatGenes().add(gene);
            }
            else if( gene.getSpeciesTypeKey()== SpeciesType.MOUSE ) {
                rec.getMouseGenes().add(gene);
            }
        }
    }

    public void generateReportForUnmatchingSmpdbIds() throws IOException {

        backupReportForUnmatchingSmpdbIds();

        BufferedWriter writer = new BufferedWriter(new FileWriter(getUnmatchingSmpdbIdsReportFile()));

        // generate list of SMPDB ids not matching pathway ontology in RGD
        StringBuilder bufSmpdbIdsNotMatchingPathway = new StringBuilder();
        int noUnmatchable = 0, noMatchable = 0;
        for( Map.Entry<String, List<Term>> entry: smpdbIdsToPathways.entrySet() ) {
            if( entry.getValue().isEmpty() ) {
                noUnmatchable++;
                bufSmpdbIdsNotMatchingPathway
                    .append(noUnmatchable).append("|")
                    .append(entry.getKey()).append("|")
                    .append(smpdbIdsToPathwayName.get(entry.getKey()))
                    .append("\n");
            } else {
                noMatchable++;
            }
        }

        int total = smpdbIdsUnmatchable.size() + noUnmatchable + noMatchable;

        // generate header
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        writer.write("======================================================\n");
        writer.write("DATE:   "+currentDate+"\n");
        writer.write("COUNT: SMPDB ids not matching pathway ontology in RGD: "+showCountAndPercentage(noUnmatchable, total));
        writer.write("COUNT: SMPDB ids marked as non-matchable by Victoria: "+showCountAndPercentage(smpdbIdsUnmatchable.size(), total));
        writer.write("COUNT: SMPDB ids matching pathway ontology in RGD: "+showCountAndPercentage(noMatchable, total));
        writer.write("\n");
        writer.write("======================================================\n");
        writer.write("REPORT: SMPDB ids not matching pathway ontology in RGD\n");
        writer.write("------------------------------------------------------\n");
        writer.write("No|SMPDB ID|Pathway Name\n");

        // generate list of SMPDB ids not matching pathway ontology in RGD
        writer.write(bufSmpdbIdsNotMatchingPathway.toString());

        writer.write("\n");
        writer.write("======================================================\n");

        // generate list of SMPDB ids marked as unmatchable by Victoria
        writer.write("REPORT: SMPDB ids marked as non-matchable by Victoria\n");
        writer.write("------------------------------------------------------\n");
        writer.write("No|SMPDB ID|Pathway Name\n");

        // sort unmatchable SMPDB ids by key
        Map<String,String> unmatchables = new TreeMap<>(smpdbIdsUnmatchable);

        noUnmatchable = 0;
        for( Map.Entry<String, String> entry: unmatchables.entrySet() ) {
            noUnmatchable++;
            writer.write(noUnmatchable+"|"+entry.getKey()+"|"+entry.getValue()+"\n");
        }

        writer.write("======================================================\n");

        writer.close();
    }

    String showCountAndPercentage(int count, int total) {
        return String.format(" %d (%.1f%%)\n", count, (100.0f*count)/total);
    }

    // if there is an old report with unmatching smpdb ids that is older than a day,
    // backup it by appending file date to its name
    void backupReportForUnmatchingSmpdbIds() {

        File file = new File(getUnmatchingSmpdbIdsReportFile());
        if( file.exists() ) {
            // get the current date and truncate it to the beginning of the day
            Date date = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if( file.lastModified() < cal.getTimeInMillis() ) {
                // backup the file
                String prefix = new SimpleDateFormat("yyyy-MM-dd_").format(new Date(file.lastModified()));
                File newFile = new File(prefix+getUnmatchingSmpdbIdsReportFile());
                if( file.renameTo(newFile) ) {
                    System.out.println(getUnmatchingSmpdbIdsReportFile()+" renamed to "+newFile.getPath());
                }
            }
        }
    }

    public void setUnmatchingSmpdbIdsReportFile(String unmatchingSmpdbIdsReportFile) {
        this.unmatchingSmpdbIdsReportFile = unmatchingSmpdbIdsReportFile;
    }

    public String getUnmatchingSmpdbIdsReportFile() {
        return unmatchingSmpdbIdsReportFile;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setRefRgdId(int refRgdId) {
        this.refRgdId = refRgdId;
    }

    public int getRefRgdId() {
        return refRgdId;
    }

    public void setDataSrc(String dataSrc) {
        this.dataSrc = dataSrc;
    }

    public String getDataSrc() {
        return dataSrc;
    }

    public void setNonmatchableSmpdbIdsFile(String nonmatchableSmpdbIdsFile) {
        this.nonmatchableSmpdbIdsFile = nonmatchableSmpdbIdsFile;
    }

    public String getNonmatchableSmpdbIdsFile() {
        return nonmatchableSmpdbIdsFile;
    }
}
