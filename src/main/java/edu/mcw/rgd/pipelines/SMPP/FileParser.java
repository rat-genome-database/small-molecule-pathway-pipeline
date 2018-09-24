package edu.mcw.rgd.pipelines.SMPP;

import edu.mcw.rgd.pipelines.RecordPreprocessor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author mtutaj
 * @since 10/27/2015
 * <p>
 * open the input zip file, extract proteins.csv and process it line by line;
 * input file format:
 * <pre>
 SMPDB ID,Pathway Name,Pathway Subject,Uniprot ID,Protein Name,HMDBP ID,DrugBank ID,GenBank ID,Gene Name,Locus
 SMP00055,Alanine Metabolism,Metabolic,P49588,"Alanine--tRNA ligase, cytoplasmic",HMDBP00625,,AC012184,AARS,16q22
 * </pre>
 */
public class FileParser extends RecordPreprocessor{

    private String inputFileName;
    private int recno = 0;
    private List<String> ignoredPathwayPrefix;

    public FileParser() {
    }

    public void setInputFileName(String inputFileName) {
        this.inputFileName = inputFileName;
    }

    @Override
    public void process() throws Exception {

        List<Record> records = new ArrayList<>();

        //open zip file -- process proteins.csv
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(inputFileName));
        ZipEntry entry;
        while( (entry=zipIn.getNextEntry())!=null ) {
            if( entry.getName().endsWith("proteins.csv") ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zipIn));

                // parse fields
                String line = reader.readLine();
                List<String> headers = parseFields(line);

                Set<String> smpIds = new HashSet<>();
                Set<String> geneSymbols = new HashSet<>();

                // process lines
                while( (line=reader.readLine())!=null ) {
                    Record rec = parseLine(line, headers);
                    if( rec!=null ) {
                        smpIds.add(rec.getSMPDbId());
                        geneSymbols.add(rec.getGeneSymbol());
                        records.add(rec);

                        getSession().incrementCounter("PARSER: lines parsed and processed", 1);
                    } else {
                        getSession().incrementCounter("PARSER: lines skipped total", 1);
                    }
                }

                getSession().incrementCounter("PARSER: SMPDB IDs processed ", smpIds.size());
                getSession().incrementCounter("PARSER: symbols processed ", geneSymbols.size());
            } else {
                System.out.println("Unexpected file " + entry.getName() + " in " + inputFileName);
            }
            zipIn.closeEntry();
        }
        zipIn.close();

        // randomize records to minimize waits on synchronized blocks
        Collections.shuffle(records);

        // start processing
        for( Record rec: records ) {
            rec.setRecNo(++recno);
            this.getSession().putRecordToFirstQueue(rec);
        }
    }

    Record parseLine(String line, List<String> headers) throws Exception {

        Record rec = new Record();

        List<String> fields = parseFields(line);
        for( int i=0; i<fields.size(); i++ ) {
            String field = fields.get(i);
            switch(headers.get(i)) {
                case "SMPDB ID": // SMP00055
                    if (field.length() != 8 || !field.startsWith("SMP")) {
                        System.out.println("PARSE ERROR: unexpected SMPDB ID: " + field);
                        return null;
                    }
                    rec.setSMPDbId("SMP:"+field.substring(3));
                    break;

                case "Pathway Name":
                    for( String ignoredPrefix: ignoredPathwayPrefix ) {
                        if( field.startsWith(ignoredPrefix) ) {
                            getSession().incrementCounter("PARSER: lines skipped for pathway prefix ["+ignoredPrefix+"]", 1);
                            return null;
                        }
                    }
                    rec.setPathwayName(field);
                    break;

                case "Pathway Subject":
                    rec.setPathwaySubject(field);
                    break;

                case "Uniprot ID":
                    rec.setUniprotId(field);
                    break;

                case "Protein Name":
                    rec.setProteinName(field);
                    break;

                case "HMDBP ID": // HMDBP00625
                    if (field.length()>0 && (field.length() != 10 || !field.startsWith("HMDBP"))) {
                        System.out.println("PARSE ERROR: unexpected HMDBP ID: " + field);
                        return null;
                    }
                    rec.setHmdbpID(field);
                    break;

                case "DrugBank ID":
                    rec.setDrugBankId(field);
                    break;

                case "GenBank ID":
                    rec.setGenBankId(field);
                    break;

                case "Gene Name":
                    rec.setGeneSymbol(field);
                    break;

                case "Locus":
                    rec.setLocus(field);
                    break;

                default:
                    System.out.println("PARSE ERROR: unexpected field: " + headers.get(i)+" ["+field+"]");
                    return null;
            }

        }

        return rec;
    }

    // fields are comma separated, but fields could be also surrounded by double quotes
    // SMP00055,Alanine Metabolism,Metabolic,P49588,"Alanine--tRNA ligase, cytoplasmic",HMDBP00625,,AC012184,AARS,16q22
    List<String> parseFields(String line) {

        List<String> fields = new ArrayList<>();
        int fieldStart = 0, commaPos, quotePos, quotePos2;
        while( fieldStart>=0 ) {
            commaPos = line.indexOf(',', fieldStart);
            quotePos = line.indexOf('\"', fieldStart);
            if( commaPos<0 ) {
                // no more fields in the line -- handle the last field
                if( quotePos<0 ) {
                    // no quoted data
                    fields.add(line.substring(fieldStart).trim());
                } else {
                    // find the second quote
                    quotePos2 = line.indexOf('\"', quotePos+1);
                    if( quotePos2>0 ) {
                        fields.add(line.substring(quotePos+1, quotePos2));
                    } else {
                        // non-matching second quote
                        System.out.println("PARSE ERROR: non-matching double quotes for line:");
                        System.out.println(line);
                        return null;
                    }
                }

                fieldStart = -1; // terminate the loop
            }
            else { // there are 2+ fields in the line
                if( quotePos<0 || quotePos>commaPos ) {
                    // unquoted field
                    fields.add(line.substring(fieldStart, commaPos).trim());

                    fieldStart = commaPos+1;
                } else {
                    // quoted field
                    // find the second quote
                    quotePos2 = line.indexOf('\"', quotePos+1);
                    if( quotePos2>0 ) {
                        fields.add(line.substring(quotePos+1, quotePos2));
                    } else {
                        // non-matching second quote
                        System.out.println("PARSE ERROR: non-matching double quotes for line:");
                        System.out.println(line);
                        return null;
                    }

                    commaPos = line.indexOf(',', quotePos2);
                    if( commaPos<0 ) {
                        fieldStart = -1;
                    } else {
                        fieldStart = commaPos+1;
                    }
                }
            }
        }
        return fields;
    }

    public void setIgnoredPathwayPrefix(List<String> ignoredPathwayPrefix) {
        this.ignoredPathwayPrefix = ignoredPathwayPrefix;
    }

    public List<String> getIgnoredPathwayPrefix() {
        return ignoredPathwayPrefix;
    }
}
