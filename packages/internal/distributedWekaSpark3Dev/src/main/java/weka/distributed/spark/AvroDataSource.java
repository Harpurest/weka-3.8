/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    AvroDataSource
 *    Copyright (C) 2016 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.distributed.spark;

import distributed.core.DistributedJob;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SparkSession;
import weka.core.Utils;
import weka.distributed.DistributedWekaException;

import java.io.IOException;
import java.util.List;

/**
 * DataSource that uses Databricks spark-avro library to read an Avro file into
 * a {@code Dataset<Row>}
 * 
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class AvroDataSource extends FileDataSource {

  private static final long serialVersionUID = 7407690932232117753L;

  public AvroDataSource() {
    setJobName("Avro data source");
    setJobDescription("Source data from an Avro file via Dataset<Row>s");
  }

  @Override
  public boolean runJobWithContext(JavaSparkContext sparkContext)
    throws IOException, DistributedWekaException {

    super.runJobWithContext(sparkContext);

    SparkContext sc = JavaSparkContext.toSparkContext(sparkContext);

    // SQLContext sqlContext = new SQLContext(sparkContext);
    // TODO SQLContext is deprecated - need to move to SparkSession directly
    SparkSession sparkSession = SparkSession.builder().config(sc.getConf()).getOrCreate();
    // SQLContext sqlContext = new SQLContext(SparkSession.builder().config(sc.getConf()).getOrCreate());

    // TODO could allow multiple paths...
    String resolvedPath =
      SparkUtils
        .resolveLocalOrOtherFileSystemPath(environmentSubstitute(getInputFile()));

    // TODO this almost certainly won't work (as databricks stuff has been assimilated?)
    // TODO need to figure out how to read avro now
    Dataset<Row> df = sparkSession.read().format("avro").load(resolvedPath);
    //Dataset<Row> df =
    //  sqlContext.read().format("com.databricks.spark.avro").load(resolvedPath);

    df = applyColumnNamesOrNamesFile(df);
    // df = executeSQL(df, sqlContext);
    df = executeSQL(df, sparkSession);
    df = unionWithExisting(df);
    df = applyPartitioning(df);
    persist(df);

    m_datasetManager.setDataset(m_datasetType.toString(), new WDDataset(df));
    if (getDebug()) {
      System.err.println("Schema:\n");
      df.printSchema();
      logMessage("Number of rows in Dataset<Row>: " + df.count());
      logMessage("First 5 rows:\n");
      List<Row> rowList = df.takeAsList(5);
      for (Row r : rowList) {
        logMessage(r.toString());
      }
    }

    runDataSinkIfNecessary(sparkContext);
    return true;
  }

  @Override
  public void run(Object toRun, String[] options) {
    if (!(toRun instanceof AvroDataSource)) {
      throw new IllegalArgumentException("Object to run is not a "
        + "AvroDataSource");
    }

    try {
      AvroDataSource ds = (AvroDataSource) toRun;

      if (Utils.getFlag('h', options)) {
        String help = DistributedJob.makeOptionsStr(ds);
        System.err.println(help);
        System.exit(1);
      }

      ds.setOptions(options);
      ds.runJob();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static void main(String[] args) {
    AvroDataSource ds = new AvroDataSource();
    ds.run(ds, args);
  }
}
