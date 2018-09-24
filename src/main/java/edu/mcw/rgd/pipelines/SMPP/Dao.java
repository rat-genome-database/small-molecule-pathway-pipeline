package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by mtutaj on 10/27/2015.
 * <p>
 * a wrapper class to centralize all database to/from traffic
 */
public class Dao {

    protected final Log logInserted = LogFactory.getLog("annots_inserted");
    protected final Log logDeleted = LogFactory.getLog("annots_deleted");

    private AnnotationDAO annotationDAO = new AnnotationDAO();
    private GeneDAO geneDAO = new GeneDAO();
    private OntologyXDAO ontologyXDAO = new OntologyXDAO();
    private XdbIdDAO xdbIdDAO = new XdbIdDAO();

    private static final Gene NULL_GENE = new Gene();

    public Dao() {
        System.out.println(geneDAO.getConnectionInfo());
    }

    public List<Term> getPathwaysForSmpdbId(String smpdbId) throws Exception {

        // one time load
        if( _mapSMP2PW.isEmpty() ) {
            for( TermSynonym syn: ontologyXDAO.getActiveSynonymsByNamePattern("PW", "SMP:%") ) {
                List<String> pwIds = _mapSMP2PW.get(syn.getName());
                if( pwIds==null ) {
                    pwIds = new ArrayList<>();
                    _mapSMP2PW.put(syn.getName(), pwIds);
                }
                pwIds.add(syn.getTermAcc());
            }
        }

        List<String> pwIds = _mapSMP2PW.get(smpdbId);
        if( pwIds==null ) {
            return Collections.emptyList();
        }

        List<Term> terms = new ArrayList<>();
        for( String pwId: pwIds ) {
            terms.add(ontologyXDAO.getTermByAccId(pwId));
        }
        return terms;
    }
    static private Map<String, List<String>> _mapSMP2PW = new HashMap<>();


    public synchronized Gene getHumanGeneBySymbol(String symbol) throws Exception {

        symbol = symbol.toUpperCase();
        Gene humanGene = _geneCache.get(symbol);
        if( humanGene==null ) {
            // human gene not in cache -- get it from db
            List<Gene> genes = geneDAO.getActiveGenes(SpeciesType.HUMAN, symbol);

            if (genes.size()==1 ) {
                humanGene = genes.get(0);
            } else {
                if (genes.size() > 1) {
                    System.out.println("WARN: multiple genes in RGD for symbol " + symbol);
                    humanGene = genes.get(0);
                } else {
                    humanGene = NULL_GENE;
                }
            }

            _geneCache.put(symbol, humanGene);
        }
        return humanGene==NULL_GENE? null : humanGene;
    }
    static private Map<String,Gene> _geneCache = new HashMap<>();


    public synchronized Gene getHumanGeneByUniprotId(String uniprotId) throws Exception {

        uniprotId = uniprotId.toUpperCase();
        Gene humanGene = _uniprotCache.get(uniprotId);
        if( humanGene==null ) {
            // human gene not in cache -- get it from db
            List<Gene> genes = xdbIdDAO.getGenesByXdbId(XdbId.XDB_KEY_UNIPROT, uniprotId, SpeciesType.HUMAN);

            if (genes.size()==1 ) {
                humanGene = genes.get(0);
            } else {
                if (genes.size() > 1) {
                    System.out.println("WARN: multiple genes in RGD for UniprotId " + uniprotId);
                    humanGene = genes.get(0);
                } else {
                    humanGene = NULL_GENE;
                }
            }
            _uniprotCache.put(uniprotId, humanGene);
        }
        return humanGene==NULL_GENE? null : humanGene;
    }
    static private Map<String,Gene> _uniprotCache = new HashMap<>();


    public synchronized Gene getHumanGeneByGenBankId(String genBankId) throws Exception {

        genBankId = genBankId.toUpperCase();
        Gene humanGene = _genBankCache.get(genBankId);
        if( humanGene==null ) {
            // human gene not in cache -- get it from db
            List<Gene> genes = xdbIdDAO.getGenesByXdbId(XdbId.XDB_KEY_GENEBANKNU, genBankId, SpeciesType.HUMAN);
            if (genes.size()==1 ) {
                humanGene = genes.get(0);
            } else {
                if (genes.size() > 1) {
                    System.out.println("WARN: multiple genes in RGD for GenBank Id " + genBankId);
                    humanGene = genes.get(0);
                } else {
                    humanGene = NULL_GENE;
                }
            }
            _genBankCache.put(genBankId, humanGene);
        }
        return humanGene==NULL_GENE? null : humanGene;
    }
    static private Map<String,Gene> _genBankCache = new HashMap<>();


    public synchronized Gene getHumanGeneByAlias(String alias) throws Exception {

        alias = alias.toUpperCase(); // geneDAO.getGenesByAlias() is case-insensitive
        Gene humanGene = _aliasCache.get(alias);
        if( humanGene==null ) {
            // human gene not in cache -- get it from db
            List<Gene> genes = geneDAO.getGenesByAlias(alias, SpeciesType.HUMAN);
            if (genes.size()==1 ) {
                humanGene = genes.get(0);
            } else {
                if (genes.size() > 1) {
                    System.out.println("WARN: multiple genes in RGD for alias " + alias);
                }
                humanGene = NULL_GENE;
            }
            _aliasCache.put(alias, humanGene);
        }
        return humanGene==NULL_GENE? null : humanGene;
    }
    static private Map<String,Gene> _aliasCache = new HashMap<>();


    synchronized public List<Gene> getOrthologs(int rgdId) throws Exception {

        List<Gene> genes = _orthoCache.get(rgdId);
        if( genes==null ) {
            genes = geneDAO.getActiveOrthologs(rgdId);
            _orthoCache.put(rgdId, genes);
        }
        return genes;
    }
    static private Map<Integer,List<Gene>> _orthoCache = new HashMap<>();

    public int getAnnotationKey(Annotation annot) throws Exception {
        return annotationDAO.getAnnotationKey(annot);
    }

    public void insertAnnotation(Annotation annot) throws Exception {
        annotationDAO.insertAnnotation(annot);
        logInserted.info(annot.dump("|"));
    }

    public void updateLastModified(int annotKey, int lastModifiedBy) throws Exception {
        annotationDAO.updateLastModified(annotKey, lastModifiedBy);
    }

    public int deleteStaleAnnotations(Date cutOffDate, int createdBy, String deleteThresholdStr) throws Exception {

        // extract delete threshold in percent
        int percentPos = deleteThresholdStr.indexOf('%');
        int deleteThreshold = Integer.parseInt(deleteThresholdStr.substring(0, percentPos));

        int currentAnnotCount = annotationDAO.getCountOfAnnotationsForCreatedBy(createdBy);
        List<Annotation> annotsForDelete = annotationDAO.getAnnotationsModifiedBeforeTimestamp(createdBy, cutOffDate);
        for( Annotation a: annotsForDelete ) {
            logDeleted.info(a.dump("|"));
        }

        int annotsForDeleteCount = annotsForDelete.size();
        int annotsForDeleteThreshold = (deleteThreshold * currentAnnotCount) / 100; // 5% delete threshold

        if( annotsForDeleteCount > annotsForDeleteThreshold ) {
            System.out.println(" STALE ANNOTATIONS DELETE THRESHOLD ("+deleteThresholdStr+") -- "+annotsForDeleteThreshold);
            System.out.println(" STALE ANNOTATIONS TAGGED FOR DELETE     -- "+annotsForDeleteCount);
            System.out.println(" STALE ANNOTATIONS DELETE THRESHOLD ("+deleteThresholdStr+") EXCEEDED -- no annotations deleted");
            return 0;
        }
        return annotationDAO.deleteAnnotations(createdBy, cutOffDate);
    }
}
