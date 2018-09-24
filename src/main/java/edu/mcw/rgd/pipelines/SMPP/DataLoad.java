package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.pipelines.PipelineRecord;
import edu.mcw.rgd.pipelines.RecordProcessor;

/**
 * Created by mtutaj on 10/27/2015.
 */
public class DataLoad extends RecordProcessor {

    private Dao dao;

    public DataLoad(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void process(PipelineRecord pipelineRecord) throws Exception {

        Record rec = (Record) pipelineRecord;

        if( !rec.getForInsertAnnots().isEmpty() ) {
            for (Annotation ann : rec.getForInsertAnnots()) {
                dao.insertAnnotation(ann);
                getSession().incrementCounter("ZL: total annotations inserted", 1);
                getSession().incrementCounter("ZL: "+ SpeciesType.getCommonName(ann.getSpeciesTypeKey()).toLowerCase()+" annotations inserted", 1);
            }
        }

        if( !rec.getForUpdateAnnots().isEmpty() ) {
            for (Annotation ann : rec.getForUpdateAnnots()) {
                dao.updateLastModified(ann.getKey(), ann.getCreatedBy());
                getSession().incrementCounter("ZL: total annotations matching", 1);
                getSession().incrementCounter("ZL: "+ SpeciesType.getCommonName(ann.getSpeciesTypeKey()).toLowerCase()+" annotations matching", 1);
            }
        }
    }
}
