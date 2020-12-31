from StatFunctions import *

yearsDFtotal = pd.DataFrame()

# The last number not included in the range! (max 2020)
for year in range(firstYear,lastYear):
    yearsDFtotal = yearsDFtotal.append(getUseCasesFromCSV(csvPath+"newDescriptors_"+str(year+1)+".csv", "newDescriptors_"))

yearsDFtotal.to_csv(path + "yearsDFtotal.csv", ";")


# For the paper

# Table 2.  Overall frequencies per provenance Category and Type
getPercentages(yearsDFtotal, cProvCodeField)
getPercentages(yearsDFtotal, cProvCategoryField)
getPercentages(yearsDFtotal, cProvTypeField)
getPercentages(yearsDFtotal, cProvTypeField, deserialize = False)

# Fig. 12.  Provenance categories per year
getLineChart(yearsDFtotal, cProvCategoryField)

# Fig. 13   MeSH categories per year
getLineChart(yearsDFtotal, categoriesField)

# Fig. 14.  Provenance types per year
getLineChart(yearsDFtotal, cProvTypeField)

# Fig. 15.  Provenance types (single) per year
getLineChart(yearsDFtotal, cProvCodeField, deserialize = False) # Without de-serialization to consider combinations

# Additional diagrams
# getBarChart(yearsDFtotal, phCountField)
# getBarChart(yearsDFtotal, cProvCategoryField)
# getBarChart(yearsDFtotal, cProvCodeField,deserialize = False) # Without de-serialization to consider combinations
# getBarChart(yearsDFtotal, cProvCodeField) # With deserialization to ignore combinations
# getLineChart(yearsDFtotal, cProvTypeField, underflow= 5)
# getLineChart(yearsDFtotal, cProvTypeField, provLevelPrefix="2")
# getLineChart(yearsDFtotal, cProvTypeField, provLevelPrefix="3")
# getLineChart(yearsDFtotal, cProvCodeField, deserialize = False, underflow = 10) # Without de-serialization to consider combinations
# getBarChart(yearsDFtotal, phCountField, overflowLimit=6, FGDiseaseOnly=False, provLevelPrefix="2")
# getBarChart(yearsDFtotal, phCountField, overflowLimit=6, FGDiseaseOnly=False, provLevelPrefix="1")
# getPercentages(yearsDFtotal, cProvTypeField, provLevelPrefix="3")
# getPercentages(yearsDFtotal, cProvTypeField, provLevelPrefix="2")
# getPercentages(yearsDFtotal, cProvTypeField, provLevelPrefix="1")
# getBarChart(yearsDFtotal, phMutirelatedSizeField)
# getBarChart(yearsDFtotal, phRelationField, sortByCount = True)
# getPairsPlot(yearsDFtotal, provLevelPrefix="2")
# getPairsPlot(yearsDFtotal, provLevelPrefix="3")
# getCorrelationChart(yearsDFtotal, provLevelPrefix="2")
# getCorrelationChart(yearsDFtotal, provLevelPrefix="3")

