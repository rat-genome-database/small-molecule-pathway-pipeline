package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.pipelines.PipelineRecord;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by mtutaj on 10/27/2015.
 * <p>
 * represents a line read from input file
 */
public class Record extends PipelineRecord {

    /// gene match type for human gene
    public enum GENE_MATCH_TYPE {
        GENE_MATCH_NONE,            // gene symbol could not be matched
        GENE_MATCH_BY_SYMBOL,       // gene matches by symbol
        GENE_MATCH_BY_UNIPROT_ID,   // gene matches by uniprot id
        GENE_MATCH_BY_GENBANK_ID,   // gene matches by GenBank id
        GENE_MATCH_BY_ALIAS,        // gene matches by alias
    };

    // incoming data
    private String SMPDbId;
    private String pathwayName;
    private String pathwaySubject;
    private String uniprotId;
    private String proteinName;
    private String hmdbpID;
    private String drugBankId;
    private String genBankId;
    private String geneSymbol;
    private String locus;

    // qc data
    private List<Term> matchingPathwayTerms;
    private GENE_MATCH_TYPE humanGeneMatchType;
    private Gene humanGene;
    private List<Gene> ratGenes = new ArrayList<>();
    private List<Gene> mouseGenes = new ArrayList<>();

    private List<Annotation> forInsertAnnots = new ArrayList<>();
    private List<Annotation> forUpdateAnnots = new ArrayList<>();

    // clean up all qc fields
    public void qcInit() {
        matchingPathwayTerms = null;
        humanGeneMatchType = GENE_MATCH_TYPE.GENE_MATCH_NONE;
        humanGene = null;
        ratGenes.clear();
        mouseGenes.clear();
        forInsertAnnots.clear();
        forUpdateAnnots.clear();
    }

    public String getSMPDbId() {
        return SMPDbId;
    }

    public void setSMPDbId(String SMPDbId) {
        this.SMPDbId = SMPDbId;
    }

    public String getPathwayName() {
        return pathwayName;
    }

    public void setPathwayName(String pathwayName) {
        this.pathwayName = pathwayName;
    }

    public String getPathwaySubject() {
        return pathwaySubject;
    }

    public void setPathwaySubject(String pathwaySubject) {
        this.pathwaySubject = pathwaySubject;
    }

    public String getUniprotId() {
        return uniprotId;
    }

    public void setUniprotId(String uniprotId) {
        this.uniprotId = uniprotId;
    }

    public String getProteinName() {
        return proteinName;
    }

    public void setProteinName(String proteinName) {
        this.proteinName = proteinName;
    }

    public String getHmdbpID() {
        return hmdbpID;
    }

    public void setHmdbpID(String hmdbpID) {
        this.hmdbpID = hmdbpID;
    }

    public String getDrugBankId() {
        return drugBankId;
    }

    public void setDrugBankId(String drugBankId) {
        this.drugBankId = drugBankId;
    }

    public String getGenBankId() {
        return genBankId;
    }

    public void setGenBankId(String genBankId) {
        this.genBankId = genBankId;
    }

    public String getGeneSymbol() {
        return geneSymbol;
    }

    public void setGeneSymbol(String geneSymbol) {
        this.geneSymbol = geneSymbol;
    }

    public String getLocus() {
        return locus;
    }

    public void setLocus(String locus) {
        this.locus = locus;
    }

    public List<Term> getMatchingPathwayTerms() {
        return matchingPathwayTerms;
    }

    public void setMatchingPathwayTerms(List<Term> matchingPathwayTerms) {
        this.matchingPathwayTerms = matchingPathwayTerms;
    }

    public Gene getHumanGene() {
        return humanGene;
    }

    public void setHumanGene(Gene humanGene) {
        this.humanGene = humanGene;
    }

    public GENE_MATCH_TYPE getHumanGeneMatchType() {
        return humanGeneMatchType;
    }

    public void setHumanGeneMatchType(GENE_MATCH_TYPE humanGeneMatchType) {
        this.humanGeneMatchType = humanGeneMatchType;
    }

    public List<Gene> getRatGenes() {
        return ratGenes;
    }

    public List<Gene> getMouseGenes() {
        return mouseGenes;
    }

    public List<Annotation> getForInsertAnnots() {
        return forInsertAnnots;
    }

    public List<Annotation> getForUpdateAnnots() {
        return forUpdateAnnots;
    }
}
