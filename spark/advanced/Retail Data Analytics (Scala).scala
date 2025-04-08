// Databricks notebook source
// MAGIC %md
// MAGIC # Retail Data Wrangling and Analytics

// COMMAND ----------

// MAGIC %md
// MAGIC This notebook focuses on providing analytics of a UK based, online retail store. The objective is to uncover meaningful insights that can support data-driven decision-making and improve business outcomes.
// MAGIC
// MAGIC The data wrangling process includes handling missing values, parsing and formatting dates, and ensuring that all columns are in the appropriate data types for downstream analysis. From there, the analysis explores customer behavior over time, including patterns in monthly active users and overall sales performance. A key part of the project involves performing RFM (Recency, Frequency, Monetary) analysis to segment customers based on purchasing behavior. These insights provide a foundation for future analysis, including customer retention strategies and sales forecasting.

// COMMAND ----------

// MAGIC %md
// MAGIC ### Load Dataset and Import **Libraries**

// COMMAND ----------

// Import functions
import org.apache.spark.sql.functions._

// Load Dataset
val Retail_Df = spark.read.format("csv")
  .option("header", "true")
  .option("inferSchema", "true")
  .load("/FileStore/tables/online_retail.csv")

display(Retail_Df)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Clean and Format Data

// COMMAND ----------

// Drop null values and add Invoice Total & Month Columns
val Df = Retail_Df.na.drop()
  .withColumn("InvoiceTotal", round(col("Quantity") * col("Price"), 2))
  .withColumn("Month", date_format(col("InvoiceDate"), "yyyy-MM"))

// Verify column count results
Df.columns.foreach { colName =>
  val Count = Df.filter(Df(colName).isNotNull).count()
  println(s"$colName: $Count")
}

// COMMAND ----------

// MAGIC %md
// MAGIC ### Total Invoice Amount Distribution

// COMMAND ----------

// Calculate invoice totals
val Invoice_Totals = Df.groupBy("Invoice")
  .agg(sum("InvoiceTotal").alias("InvoiceTotal"))

// Use 85th percentile to remove outliers
val Quantile_85 = Invoice_Totals.stat.approxQuantile("InvoiceTotal", Array(0.85), 0.01)(0)
val Filtered_ITs = Invoice_Totals.filter((col("InvoiceTotal") > 0) && (col("InvoiceTotal") < Quantile_85))

display(Filtered_ITs)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Monthly Placed and Canceled Orders

// COMMAND ----------

// Count monthly placed orders
val Monthly_Placed = (
  Df.dropDuplicates("Invoice")
  .filter(col("quantity") > 0)
  .groupBy("Month")
  .agg(count("*").alias("OrdersPlaced"))
)

// Count monthly cancelled orders
val Monthly_Canceled = (
  Df.dropDuplicates("Invoice")
  .filter(col("quantity") < 0)
  .groupBy("Month")
  .agg(count("*").alias("OrdersCanceled"))
)

// Join data for visualization
val Monthly_PC = (
  Monthly_Placed.join(Monthly_Canceled, "Month", "inner")
  .orderBy("Month")
)

display(Monthly_PC)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Monthly Sales

// COMMAND ----------

// Calculate total sales by month
val Monthly_Sales = (
    Df.groupBy("Month")
    .agg(round(sum("InvoiceTotal"), 2).alias("MonthlySales"))
    .orderBy("Month")
)

display(Monthly_Sales)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Monthly Sales Growth

// COMMAND ----------

import org.apache.spark.sql.expressions.Window

// Calculate the change(%) in sales by month
val Monthly_Sales_Growth = (
    Monthly_Sales.withColumn("Growth (%)", ((col("MonthlySales") - lag("MonthlySales", 1).over(Window.orderBy("Month"))) / lag("MonthlySales", 1).over(Window.orderBy("Month")) * 100))
)

display(Monthly_Sales_Growth)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Monthly Active Users

// COMMAND ----------

// Count the number of unique users that placed orders each month
val Monthly_Users = (
  Df.withColumn("Month", date_format(col("InvoiceDate"), "yyyy-MM"))
  .dropDuplicates("Month","Customer ID")
  .groupBy("Month")
  .agg(count("Customer ID").alias("Active Users"))
  .orderBy("Month")
)

display(Monthly_Users)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Monthly New/Existing Users

// COMMAND ----------


// Partition for finding customer's first order month
val window = Window.partitionBy("Customer ID")

// Find new/existing user counts for each month
val NE_Users = (
  Df.withColumn("FirstMonth", min("Month").over(window))
  .withColumn("UserType",when(col("Month") === col("FirstMonth"), "New").otherwise("Existing"))
  .dropDuplicates("Customer ID", "Month")
  .groupBy("Month")
  .agg(
        count(when(col("UserType") === "New", lit(true))).alias("New Users"),
        count(when(col("UserType") === "Existing", lit(true))).alias("Existing Users"),
  )
  .orderBy("Month")
)

display(NE_Users)


// COMMAND ----------

// MAGIC %md
// MAGIC ### RFM

// COMMAND ----------

import org.apache.spark.sql.types.DoubleType

// Add Duration Column
val Today = current_timestamp()
val RFM = Df.withColumn("Duration", round((to_timestamp(Today).cast("long") - col("InvoiceDate").cast("long"))/86400))

// Find Recency, Frequency, Monetary
val Recency = RFM.groupBy("Customer ID").agg(min("Duration").alias("Recency"))
val Frequency = RFM.groupBy("Customer ID").agg(countDistinct("Invoice").alias("Frequency"))
val Monetary = RFM.groupBy("Customer ID").agg(round(sum("InvoiceTotal"), 2).alias("Monetary"))

// Build RFM dataframe
val RFM_Final = (
    Recency.join(Frequency, "Customer ID")
    .join(Monetary, "Customer ID")
)

display(RFM_Final)

// COMMAND ----------

// RFM Segmentation - Segment customers into categories based on defined expectations of the business.

// Define Recency Score
def r_score(r_value: Int): Int = {
    if (r_value <= 5000) 1
    else if (r_value <= 5200) 2
    else if (r_value <= 5400) 3
    else 4
}

// Define Frequency Score     
def f_score(f_value: Int): Int = {
    if (f_value <= 1) 1
    else if (f_value <= 4) 2
    else if (f_value <= 7) 3
    else 4
}

// Define Monetary Score
def m_score(m_value: Int): Int = {
    if (m_value <= 100) 4
    else if (m_value <= 1000) 3
    else if (m_value <= 2000) 2
    else 1
}

import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.StringType

val R_udf = udf(r_score _)
val F_udf = udf(f_score _)
val M_udf = udf(m_score _)

// COMMAND ----------

import org.apache.spark.sql.functions.concat

// Create RFM Segmentation DataFrame
val RFM_Seg = (
    RFM_Final.withColumn("R Score", R_udf(col("Recency")))
    .withColumn("F Score", F_udf(col("Frequency")))
    .withColumn("M Score", M_udf(col("Monetary")))
    .withColumn("RFM Score", concat(col("R score"), col("F Score"), col("M Score")))
)

display(RFM_Seg)

// COMMAND ----------

RFM_Seg
  .groupBy("RFM Score")
  .agg(avg("Recency"),avg("Frequency"),avg("Monetary"))
  .orderBy("RFM Score")
  .show(10)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Conclusion
// MAGIC
// MAGIC This notebook provided an analysis of customer habits and the financial performance of the retail store from 2009 to 2012. It provides various models of financial measures that may be used to make informed, strategic business descisions. Additionally, the RFM analysis provides insights into specific customer trends/habits that may be used to develop a deeper understanding of the customer base.