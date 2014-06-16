package edu.yale.library.ladybird.engine.imports;


import edu.yale.library.ladybird.engine.model.FunctionConstants;
import edu.yale.library.ladybird.engine.oai.ImportSourceProcessor;
import edu.yale.library.ladybird.engine.oai.OaiProvider;
import edu.yale.library.ladybird.entity.ImportJobBuilder;
import edu.yale.library.ladybird.entity.ImportJobContents;
import edu.yale.library.ladybird.entity.ImportJobContentsBuilder;
import edu.yale.library.ladybird.entity.ImportJobExhead;
import edu.yale.library.ladybird.entity.ImportJobExheadBuilder;
import edu.yale.library.ladybird.persistence.dao.ImportJobContentsDAO;
import edu.yale.library.ladybird.persistence.dao.ImportJobDAO;
import edu.yale.library.ladybird.persistence.dao.ImportJobExheadDAO;
import edu.yale.library.ladybird.persistence.dao.hibernate.ImportJobContentsHibernateDAO;
import edu.yale.library.ladybird.persistence.dao.hibernate.ImportJobExheadHibernateDAO;
import edu.yale.library.ladybird.persistence.dao.hibernate.ImportJobHibernateDAO;
import org.slf4j.Logger;

import java.util.Date;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;


public class ImportWriter {
    private final Logger logger = getLogger(this.getClass());

    //TODO inject dao(s), etc

    private OaiProvider oaiProvider;
    private MediaFunctionProcessor mediaFunctionProcessor;
    private ImportSourceProcessor importSourceProcessor;
    private final ImportJobContentsDAO dao = new ImportJobContentsHibernateDAO();
    private final ImportJobDAO importJobDAO = new ImportJobHibernateDAO();
    private final Date JOB_EXEC_DATE = new Date(); //TODO

    /**
     * Full cycle import writing
     *
     * @param importEntityValue Abstraction on top of List<ImportEntity.Row>
     * @param importJobRequest Job Context
     * @return Import Id
     */
    public int write(ImportEntityValue importEntityValue, final ImportJobRequest importJobRequest) throws Exception {
        try {
            final int importId = writeImportJob(importJobRequest);
            importEntityValue = writeF1(importEntityValue);
            writeExHead(importId, importEntityValue.getHeaderRow().getColumns());
            writeContents(importId, importEntityValue);
            return importId;
        } catch (Exception e) {
            logger.error("Error writing.", e);
            throw e;
        }
    }

    private ImportEntityValue writeF1(ImportEntityValue importEntityValue) {
        //Process F1
        if (!importEntityValue.fieldConstantsInExhead(FunctionConstants.F1)) {
            return processF1(importEntityValue);
        }
        return importEntityValue;
    }

    /**
     * Write header ("ex head")
     *
     * @param importId import id of the job
     * @param list list of contents
     */
    public void writeExHead(final int importId, final List<ImportEntity.Column> list) {
        logger.trace("Writing spreadsheet headers. Columns list size={}", list.size());

        final ImportJobExheadDAO dao = new ImportJobExheadHibernateDAO();
        int col = 0;

        for (final ImportEntity.Column column : list) {
            ImportJobExhead entry = new ImportJobExheadBuilder().setImportId(importId).setCol(col).
                    setDate(JOB_EXEC_DATE).setValue(column.field.getName()).createImportJobExhead();
            dao.save(entry);
            logger.trace("Saved={}", entry.toString());
            col++;
        }
    }

    /**
     * Writes body to db
     *
     * @param importId import id of the job
     * @param importEntityValue helper data structure representing list<rows>
     */
    @SuppressWarnings("unchecked")
    public void writeContents(final int importId, final ImportEntityValue importEntityValue) {
        try {
            //Write import source data
            importSourceProcessor.process(importId, oaiProvider, importEntityValue);

            final List<ImportEntity.Row> rowList = importEntityValue.getContentRows();
            logger.trace("Writing spreadsheet body contents. Row list size={}", rowList.size());

            if (importEntityValue.fieldConstantsInExhead(FunctionConstants.F1)) {
                final int columnWithF1Field = importEntityValue.getFunctionPosition(FunctionConstants.F1);
            }

            //Process F3 without F1 (to keep tests working)
            if (importEntityValue.fieldConstantsInExhead(FunctionConstants.F3)
                    && !importEntityValue.fieldConstantsInExhead(FunctionConstants.F1)) {
                final int f3ColumnNum = importEntityValue.getFunctionPosition(FunctionConstants.F3);
                mediaFunctionProcessor.process(rowList, f3ColumnNum);
                logger.debug("Done media processing.");
            }

            //Process F3
            if (importEntityValue.hasFunction(FunctionConstants.F3, FunctionConstants.F1)) {
                final int f3ColumnNum = importEntityValue.getFunctionPosition(FunctionConstants.F3);
                final int f1columnNum = importEntityValue.getFunctionPosition(FunctionConstants.F1);
                mediaFunctionProcessor.process(importId, rowList, f3ColumnNum, f1columnNum);
                logger.debug("Done media processing.");
            }

            //Process F300
            if (importEntityValue.hasFunction(FunctionConstants.F300, FunctionConstants.F1))  {
                processImageReference(importEntityValue);
            }

            //Process F40 (hydra publishing staging)
            if (importEntityValue.hasFunction(FunctionConstants.F40, FunctionConstants.F1)) {
                publishHydra(importEntityValue);
            }

            //Process F11
            if (importEntityValue.hasFunction(FunctionConstants.F11, FunctionConstants.F1)) {
                //TODO check value is oid in this project
                publishHydraPointer(importEntityValue);
            }

            //Process F00
            if (importEntityValue.hasFunction(FunctionConstants.F00)) {
                processDelete(importEntityValue);
            }

            //Save all to DB table import job contents (N.B. f104/f105 column(s) also persisted):
            for (int i = 0; i < rowList.size(); i++) {
                final ImportEntity.Row row = rowList.get(i);
                final List<ImportEntity.Column> columnsList = row.getColumns();

                for (int j = 0; j < columnsList.size(); j++) {
                    final ImportEntity.Column<String> col = columnsList.get(j);
                    final ImportJobContents imjContentsEntry = new ImportJobContentsBuilder()
                            .setImportId(importId).setDate(JOB_EXEC_DATE).
                                    setCol(j).setRow(i).setValue(col.getValue()).build();
                    dao.save(imjContentsEntry);
                }
            }
        } catch (Exception e) {
            logger.error("Error writing contents", e); //ignore
        }
    }

    /**
     * Writes to import job table
     *
     * @param ctx Context containing information about the job
     * @return minted job id
     */
    public Integer writeImportJob(final ImportJobRequest ctx) {
        return importJobDAO.save(new ImportJobBuilder().setDate(JOB_EXEC_DATE).setJobDirectory(ctx.getJobDir()).
                setJobFile(ctx.getJobFile()).setUserId(ctx.getUserId()).createImportJob());
    }

    /**
     * Sets OAI Provider. Subject to removal.
     *
     * @param oaiProvider The default OaiProvider
     */
    public void setOaiProvider(final OaiProvider oaiProvider) {
        this.oaiProvider = oaiProvider;
    }

    /**
     * Sets Media Function Processor. Subject to removal
     */
    public void setMediaFunctionProcessor(final MediaFunctionProcessor mediaFunctionProcessor) {
        this.mediaFunctionProcessor = mediaFunctionProcessor;
    }

    public void setImportSourceProcessor(ImportSourceProcessor importSourceProcessor) {
        this.importSourceProcessor = importSourceProcessor;
    }

    public void publishHydra(ImportEntityValue importEntityValue) {
        HydraProcessor hydraProcessor = new HydraProcessor();
        hydraProcessor.write(importEntityValue);
    }

    public void publishHydraPointer(ImportEntityValue importEntityValue) {
        HydraPointerProcessor hydraPointerProcessor = new HydraPointerProcessor();
        hydraPointerProcessor.write(importEntityValue);
    }

    public void processDelete(ImportEntityValue importEntityValue) {
        DeleteProcessor deleteProcessor = new DeleteProcessor();
        deleteProcessor.process(importEntityValue);
    }

    public void processImageReference(ImportEntityValue importEntityValue) {
        ImageReferenceProcessor imageReferenceProcessor = new ImageReferenceProcessor();
        imageReferenceProcessor.write(importEntityValue);
    }

    private ImportEntityValue processF1(ImportEntityValue importEntityValue) {
        OidMinter oidMinter = new OidMinter();
        return oidMinter.write(importEntityValue);
    }
}
