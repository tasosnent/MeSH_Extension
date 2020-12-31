import matplotlib
matplotlib.use('Agg')
import pandas as pd
import os
import json
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import yaml as yaml

# Read the settings
settings_file = open("settings.yaml")
settings = yaml.load(settings_file, Loader=yaml.FullLoader)


# The path to save the results
path = settings["workingPath"]
# The path to read the CSV files
csvPath = settings["csvPath"]
# The years considered in the analysis
firstYear = settings["firstYear"]
lastYear = settings["lastYear"]

# All fields available
cProvCodeField = "Prov. Code"
cProvCategoryField = "Prov. Category"
cProvTypeField = "Prov. Type"
cRelField = "Conc. Rel."
dUIField = "Descr. UI"
dNameField = "Descr. Name"
phCountField = "PH count"
ctNSizeField = "#Ct"
parentDsSizeField = "#Parent Ds"
parentDsField = "Parent Ds"
phField = "PHs"
phMutirelatedSizeField = "#PHs multirelated"
phMutirelatedField = "PHs multirelated"
phRelationField = "PH relations"
descendantDsSizeField = "#Descendant Ds"
descendantDsField = "Descendant Ds"
categoriesField = "MeSH Categories"
cuiField = "CUI"
noteField = "MeSH note"
pervIndexField = "Prev. Indexing"
yearsField = "year"

# Fields serialized as strings separated with "-"
serializedFields = [cProvTypeField, cProvCodeField, phRelationField, categoriesField, phField]
# serializedFields = [cProvTypeField]
# horizontal bar fields
horizontalBarFields = [categoriesField, phRelationField]

# pd.set_option('max_colwidth', -1)

def set_size(w,h, ax=None):
    """ w, h: width, height in inches """
    if not ax: ax=plt.gca()
    l = ax.figure.subplotpars.left
    r = ax.figure.subplotpars.right
    t = ax.figure.subplotpars.top
    b = ax.figure.subplotpars.bottom
    figw = float(w)/(r-l)
    figh = float(h)/(t-b)
    ax.figure.set_size_inches(figw, figh)

def getUseCasesFromCSV(pathToScoreCSV,prefix):
    # Get filename and extract years folder name
    fileName = os.path.basename(pathToScoreCSV)
    fileName = fileName.replace('.csv','')
    fileName = fileName.replace(prefix,'')
    print(fileName)

    # Read CVS use-case file
    useCaseCSV = pd.read_csv(pathToScoreCSV, ";")  # A pandas.DataFrame with columns
    # Add years field (e.g. "_2017")
    useCaseCSV['year'] = [fileName] * len(useCaseCSV)
    # print(useCaseCSV["Train"].values)
    fieldsToRemove = [ parentDsField, descendantDsField, cuiField, noteField, pervIndexField ]
    for field in fieldsToRemove:
        if not field in useCaseCSV:
            index = fieldsToRemove.index(field)
            del fieldsToRemove[index]
    useCaseCSV = useCaseCSV.drop(fieldsToRemove, axis=1)
    return useCaseCSV

def getBarChart(yearsDFtotal, field, overflowLimit = None, FGDiseaseOnly = False, provLevelPrefix = None, deserialize = True, sortByCount = False):
    variableToPlot={}
    yearsConsidered = []
    # For any use case/descriptor (row) in any year
    for i in range(len(yearsDFtotal)):
        yearEnd = yearsDFtotal[yearsField].values[i]
        if int(yearEnd) not in yearsConsidered:
            yearsConsidered.append(int(yearEnd))
        provenanceLevel = yearsDFtotal[cProvCategoryField].values[i]

        valid = True
        # restrictionLevel = yearsDFtotal[rLevelsField].values[i]
        # if FGDiseaseOnly:
        #     if restrictionLevel.startswith("1:") or restrictionLevel.startswith("2:"):
        #         valid = False
        if not provLevelPrefix == None:
            if not provenanceLevel.startswith(provLevelPrefix):
                valid = False
        if valid:
            uid = yearsDFtotal[dUIField].values[i]
            fieldValue = yearsDFtotal[field].values[i]
            if overflowLimit != None and fieldValue >= overflowLimit:
                fieldValue = overflowLimit
            #  If serialized provenance, de-serialize it first
            if deserialize and field in serializedFields:
                rlList = str(fieldValue).split("-")
            else:
                # For non-serialized fields the list only has one element (a list used only for homogeneity of the code)
                rlList = [fieldValue]
            for fieldValue in rlList:
                if fieldValue not in variableToPlot.keys():
                    descriptorsAtThisLevel = []
                    descriptorsAtThisLevel.append(uid)
                    variableToPlot[fieldValue] = descriptorsAtThisLevel
                else:
                    variableToPlot[fieldValue].append(uid)

    title = field + " for use cases in " + str(min(yearsConsidered)) + "-" + str(max(yearsConsidered))
    fileName = field + "_" + str(min(yearsConsidered)) + "-" + str(max(yearsConsidered))
    if not deserialize :
        fileName = fileName + "_notDeserialized"
        title = title + " not de-serialized "
    fileName = fileName + ".png"
    # Get Restriction Level
    if FGDiseaseOnly:
        title = "FGD_" + title;
        fileName = "FGD_" + fileName;
    if not provLevelPrefix == None:
        title = provLevelPrefix + "_" + title;
        fileName = provLevelPrefix + "_" + fileName;
    label = field

    totalDF = pd.DataFrame();
    rlKeys = []
    rlCounts = []
    for key in variableToPlot.keys():
        # print(key, len(restrictionLevels[key]))
        rlKeys.append(key)
        rlCounts.append(len(variableToPlot[key]))

    totalDF[label] = rlKeys
    totalDF["Count"] = rlCounts
    if(sortByCount):
        totalDF = totalDF.sort_values(by=["Count"])
    else:
        totalDF = totalDF.sort_values(by=[label])
    if(overflowLimit != None):
        totalDF[label] = totalDF[label].replace(overflowLimit, str(overflowLimit) + "+")

    # print(totalDF)
    totalDF.to_csv(path + title + ".csv", ";")
    if (deserialize and field in serializedFields) or field in horizontalBarFields :
        # Horizontal orientation
        ax = totalDF.plot.barh(x=label, y='Count', rot=0)
        for i in ax.patches:
            # print(i)
            # get_width pulls left or right; get_y pushes up or down
            ax.text((i.get_width()), i.get_y() , str(i.get_width()) )
    else:
        # Vertical orientation
        ax = totalDF.plot.bar(x=label, y='Count', rot=0)
        for i in ax.patches:
            # print(i)
            # get_width pulls left or right; get_y pushes up or down
            ax.text(i.get_x() , (i.get_height() + 2) , str(i.get_height()) )

    ax.set_title(title)
    # ax.set_ylim(0, 4000)
    # ax.set_ylim(0, 300)

    # plt.show()
    # set_size(19, 15)
    set_size(9, 5)
    plt.savefig(path + fileName)

    # print(totalDF   )
    # pivot_df = totalDF.pivot(index='Restriction level', columns='Categories', values='Value')

    #Using Pearson Correlation
    # figure = plt.figure(figsize=(13,13))
    # cor = yearsDFtotal.corr()# Pearson
    # sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu)
    # figure.suptitle('Pearson correlation of uce-case properties (' + str(len(yearsDFtotal)) + " use cases from " + " & ".join(yearsConsidered) + ")", fontsize=15)
    # figure.savefig(path + "Pearson.png")

# restrLevelPrefix = 1, 2 etc
def getLineChart(yearsDFtotal, field, FGDiseaseOnly = False, provLevelPrefix = None, deserialize = True, underflow = 0):
    variableToPlot= {}
    linesConsidered = []
    # For any use case/descriptor (row) in any year
    for i in range(len(yearsDFtotal)):
        yearEnd = yearsDFtotal[yearsField].values[i]
        year = int(yearEnd)
        if year not in variableToPlot.keys():
            casesPerType = {}
            variableToPlot[year] = casesPerType

        # restrictionLevel = yearsDFtotal[rLevelsField].values[i]
        provenanceLevel = yearsDFtotal[cProvCategoryField].values[i]

        valid = True
        # if FGDiseaseOnly:
        #     if restrictionLevel.startswith("1:") or restrictionLevel.startswith("2:"):
        #         valid = False
        if not provLevelPrefix == None:
            if not provenanceLevel.startswith(provLevelPrefix):
                valid = False
        if valid:
            uid = yearsDFtotal[dUIField].values[i]
            fieldValue = yearsDFtotal[field].values[i]
            
            # For non-serialized fields the list only has one element (a list used only for homogeneity of the code)
            valueList = [fieldValue]
            #  If serialized provenance, de-serialize it first
            if deserialize and field in serializedFields:
                valueList = str(fieldValue).split("-")
            for fieldValue in valueList:
                # print(fieldValue)
                # print(variableToPlot[year].keys())
                if fieldValue not in variableToPlot[year].keys():
                    descriptorsAtThisGroup = []
                    descriptorsAtThisGroup.append(uid)
                    variableToPlot[year][fieldValue] = descriptorsAtThisGroup
                else:
                    variableToPlot[year][fieldValue].append(uid)
                if fieldValue not in linesConsidered:
                    linesConsidered.append(fieldValue)

    title = field + " in " + str(min(variableToPlot.keys())) + "-" + str(max(variableToPlot.keys()))
    fileName = field + " LineChart " + str(min(variableToPlot.keys())) + "-" + str(max(variableToPlot.keys()))
    if not deserialize :
        fileName = fileName + "_notDeserialized"
        title = title + " not de-serialized "

    # Get Restriction Level
    if FGDiseaseOnly:
        title = "FGD_" + title;
        fileName = "FGD_" + fileName;
    if not provLevelPrefix == None:
        title = provLevelPrefix + "_" + title;
        fileName = provLevelPrefix + "_" + fileName;
    plotDF = pd.DataFrame()
    plotYears = []
    plotLines = {} # "line/group" -> [countYear1, count year2, ... ]
    # Initialize line map
    for line in linesConsidered:
        plotLines[line] = []
    # Update counts per year
    rlCounts = []
    with open(path + title + ".json", 'w') as fp:
        json.dump(variableToPlot, fp)

    for currYear in variableToPlot.keys():
        # print(key, len(restrictionLevels[key]))
        plotYears.append(currYear)
        currYearLines = variableToPlot[currYear]
        # print( "for " + str(currYear))
        # print(currYearLines)
        for line in plotLines.keys():
            if line in currYearLines.keys():
                plotLines[line].append(len(currYearLines[line]))
                # print(" " + str(currYear) + " " + line + " " + str(len(currYearLines[line])))
            else:
                plotLines[line].append(0)

    # Ignore too low lines
    if underflow > 0 :
        toRemove = []
        for line in plotLines.keys():
           if max(plotLines[line]) < underflow:
               toRemove.append(line)
        # print(" >>>>> ", toRemove)
        for line in toRemove:
           del plotLines[line]

    plotDF["year"] = plotYears
    lineKeys = list(plotLines.keys())
    lineKeys.sort(reverse = True)
    for lineKey in lineKeys:
        plotDF[lineKey] = plotLines[lineKey]
    plotDF = plotDF.sort_values(by=["year"])

    # print(plotDF)
    plotDF.to_csv(path + fileName + ".csv", ";")
    ax = plotDF.plot.line(x='year',style='.-')
    ax.set_title(title)

    set_size(9, 5)
    plt.savefig(path + fileName + ".png")

def getCorrelationChart(yearsDFtotal, FGDiseaseOnly = False, provLevelPrefix = None, deserialize = True):
    fieldList = {cProvTypeField}
    casesToPlot = {}
    fieldValuesConsidered = []
    yesrConsidered = []
    # For any use case/descriptor (row) in any year
    for i in range(len(yearsDFtotal)):
        yearEnd = yearsDFtotal[yearsField].values[i]
        year = int(yearEnd)
        if year not in yesrConsidered:
            yesrConsidered.append(year)

        # restrictionLevel = yearsDFtotal[rLevelsField].values[i]
        provenanceLevel = yearsDFtotal[cProvCategoryField].values[i]

        valid = True
        # if FGDiseaseOnly:
        #     if restrictionLevel.startswith("1:") or restrictionLevel.startswith("2:"):
        #         valid = False
        if not provLevelPrefix == None:
            if not provenanceLevel.startswith(provLevelPrefix):
                valid = False
        # Restrict in descriptors with multiple provenance types
        types = yearsDFtotal[cProvTypeField];
        typeList = str(types).split("-")
        if len(typeList) <= 1:
            valid = False  # Ignore cases with single Provenance types
        if valid:
            uid = yearsDFtotal[dUIField].values[i]
            casesToPlot[uid] = {}
            for field in fieldList:
                fieldValue = yearsDFtotal[field].values[i]

                # For non-serialized fields the list only has one element (a list used only for homogeneity of the code)
                valueList = [fieldValue]
                # Add this case in the casesToPlot
                #  If serialized provenance, de-serialize it first
                if deserialize and field in serializedFields:
                    valueList = str(fieldValue).split("-")
                for fieldValue in valueList:
                    # fieldValueCode = field + "_" + fieldValue
                    fieldValueCode = fieldValue
                    if fieldValueCode not in fieldValuesConsidered:
                        fieldValuesConsidered.append(fieldValueCode)
                    casesToPlot[uid][fieldValueCode] = 1 # The actual count of type appearences/PHs is not considered

    title =  " pearson correlation in " + str(min(yesrConsidered)) + "-" + str(max(yesrConsidered))
    fileName =  " pearson correlation in " + str(min(yesrConsidered)) + "-" + str(max(yesrConsidered))
    if not deserialize :
        fileName = fileName + "_notDeserialized"
        title = title + " not de-serialized "
    fileName = fileName + ".png"
    # Get Restriction Level
    if FGDiseaseOnly:
        title = "FGD_" + title;
        fileName = "FGD_" + fileName;
    if not provLevelPrefix == None:
        title = provLevelPrefix + "_" + title;
        fileName = provLevelPrefix + "_" + fileName;
    plotDF = pd.DataFrame()
    plotDict = []
    plotColumns = {}  # "fieldname_fieldvalue" -> 1 or 0
    # Initialize line map
    for column in fieldValuesConsidered:
        plotColumns[column] = []
    # Update counts per year
    rlCounts = []
    for currCase in casesToPlot.keys():
        # print(key, len(restrictionLevels[key]))
        plotDict.append(currCase)
        currCaseFieldValues = casesToPlot[currCase]
        # print( "for " + str(currYear))
        # print(currYearLines)
        for column in plotColumns.keys():
            if column in currCaseFieldValues.keys():
                plotColumns[column].append(currCaseFieldValues[column])
                # print(" " + str(currYear) + " " + line + " " + str(len(currYearLines[line])))
            else:
                plotColumns[column].append(0)

    plotDF["UIDs"] = plotDict
    for column in plotColumns.keys():
        plotDF[column] = plotColumns[column]
    plotDF = plotDF.drop(columns=["UIDs"])
    # print(plotDF)
    # plotDF.to_csv(path + "plotDF" + ".csv", ";")
    plotDF = plotDF.reindex(sorted(plotDF.columns), axis=1)


    #Using Pearson Correlation
    cor = plotDF.corr()# Pearson
    matrix = np.triu(cor)
    sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu, mask=matrix, center = 0)

    set_size(5, 5)
    plt.title("Prov.Lev." + title, fontdict=None, loc='center')
    plt.tight_layout()
    plt.savefig(path + fileName)
    plt.close()
    # plt.show()
    # figure.clear()
    # cor = plotDF.corr(method='spearman')# Spearman rank correlation
    # sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu)
    # figure.suptitle('Spearman rank correlation ' + title , fontsize=15)
    # figure.savefig(path + "Spearman_" + fileName)
    # plt.show()

def getPairsPlot(yearsDFtotal, FGDiseaseOnly = False, provLevelPrefix = None, deserialize = True):
    fieldList = {cProvTypeField} # create pair plots for provenance types
    casesToPlot = {}
    fieldValuesConsidered = []
    yesrConsidered = []
    # For any use case/descriptor (row) in any year
    for i in range(len(yearsDFtotal)):
        yearEnd = yearsDFtotal[yearsField].values[i]
        year = int(yearEnd)
        if year not in yesrConsidered:
            yesrConsidered.append(year)

        # restrictionLevel = yearsDFtotal[rLevelsField].values[i]
        provenanceLevel = yearsDFtotal[cProvCategoryField].values[i]

        valid = True
        # if FGDiseaseOnly:
        #     if restrictionLevel.startswith("1:") or restrictionLevel.startswith("2:"):
        #         valid = False
        if not provLevelPrefix == None:
            if not provenanceLevel.startswith(provLevelPrefix):
                valid = False
        # Restrict in descriptors with multiple provenance types
        types = yearsDFtotal[cProvTypeField].values[i];
        typeList = str(types).split("-")
        # print(types, " > ", len(typeList), " > ", typeList)
        if len(typeList) <= 1 :
            valid = False # Ignore cases with single Provenance types
        if valid:
            uid = yearsDFtotal[dUIField].values[i]
            casesToPlot[uid] = {}
            for field in fieldList:
                fieldValue = yearsDFtotal[field].values[i]

                # For non-serialized fields the list only has one element (a list used only for homogeneity of the code)
                valueList = [fieldValue]
                # Add this case in the casesToPlot
                #  If serialized provenance, de-serialize it first
                if deserialize and field in serializedFields:
                    valueList = str(fieldValue).split("-")
                for fieldValue in valueList:
                    # fieldValueCode = field + "_" + fieldValue

                    # Ignore types 1 and 4
                    if not (fieldValue.startswith("1:") or fieldValue.startswith("4:")):
                        fieldValueCode = fieldValue
                        if fieldValueCode not in fieldValuesConsidered:
                            fieldValuesConsidered.append(fieldValueCode)
                        casesToPlot[uid][fieldValueCode] = 1 # The actual count of type appearences/PHs is not considered

    title =  " pairs plot in " + str(min(yesrConsidered)) + "-" + str(max(yesrConsidered))
    fileName =  " pairs plot in " + str(min(yesrConsidered)) + "-" + str(max(yesrConsidered))
    if not deserialize :
        fileName = fileName + "_notDeserialized"
        title = title + " not de-serialized "
    fileName = fileName + ".png"
    # Get Restriction Level
    if FGDiseaseOnly:
        title = "FGD_" + title;
        fileName = "FGD_" + fileName;
    if not provLevelPrefix == None:
        title = provLevelPrefix + "_" + title;
        fileName = provLevelPrefix + "_" + fileName;
    plotDF = pd.DataFrame()
    plotDict = []
    plotColumns = {}  # "fieldname_fieldvalue" -> 1 or 0
    # Initialize line map
    for column in fieldValuesConsidered:
        plotColumns[column] = []
    # Update counts per year
    rlCounts = []
    for currCase in casesToPlot.keys():
        # print(key, len(restrictionLevels[key]))
        plotDict.append(currCase)
        currCaseFieldValues = casesToPlot[currCase]
        # print( "for " + str(currYear))
        # print(currYearLines)
        for column in plotColumns.keys():
            if column in currCaseFieldValues.keys():
                plotColumns[column].append(currCaseFieldValues[column])
                # print(" " + str(currYear) + " " + line + " " + str(len(currYearLines[line])))
            else:
                plotColumns[column].append(0)

    plotDF["UIDs"] = plotDict
    for column in plotColumns.keys():
        plotDF[column] = plotColumns[column]
    plotDF.to_csv(path + title + ".csv", ";")
    plotDF = plotDF.drop(columns=["UIDs"])
    # print(plotDF)
    plotDF = plotDF.reindex(sorted(plotDF.columns), axis=1)

    # plotDF.to_csv(path + "plotDF" + ".csv", ";")

    #Using Pearson Correlation
    # cor = plotDF.corr()# Pearson
    # sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu)
    g = sns.pairplot(plotDF, kind="reg", diag_kind="hist")
    set_size(10, 10)
    # Make triangular
    # for i, j in zip(*np.triu_indices_from(g.axes, 1)):
    #     g.axes[i, j].set_visible(False)
    # print(path + fileName)
    g.fig.subplots_adjust(top = 0.95, left = 0.07, bottom = 0.05)
    g.fig.suptitle("Prov.Lev." + title)
    # plt.title("Prov.Lev." + title, fontdict=None, loc='center')
    # plt.tight_layout()
    plt.savefig(path + fileName)
    plt.close()
    # plt.show()
    # figure.clear()
    # cor = plotDF.corr(method='spearman')# Spearman rank correlation
    # sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu)
    # figure.suptitle('Spearman rank correlation ' + title , fontsize=15)
    # figure.savefig(path + "Spearman_" + fileName)
    # plt.show()

def getPercentages(yearsDFtotal, field, FGDiseaseOnly = False, provLevelPrefix = None, deserialize = True):
    variableToPlot={}
    yearsConsidered = []
    # For any use case/descriptor (row) in any year
    for i in range(len(yearsDFtotal)):
        yearEnd = yearsDFtotal[yearsField].values[i]
        if int(yearEnd) not in yearsConsidered:
            yearsConsidered.append(int(yearEnd))
        provenanceLevel = yearsDFtotal[cProvCategoryField].values[i]

        valid = True
        # restrictionLevel = yearsDFtotal[rLevelsField].values[i]
        # if FGDiseaseOnly:
        #     if restrictionLevel.startswith("1:") or restrictionLevel.startswith("2:"):
        #         valid = False
        if not provLevelPrefix == None:
            if not provenanceLevel.startswith(provLevelPrefix):
                valid = False
        if valid:
            uid = yearsDFtotal[dUIField].values[i]
            fieldValue = yearsDFtotal[field].values[i]
            #  If serialized provenance, de-serialize it first
            if deserialize and field in serializedFields:
                rlList = str(fieldValue).split("-")
            else:
                # For non-serialized fields the list only has one element (a list used only for homogeneity of the code)
                rlList = [fieldValue]
            for fieldValue in rlList:
                if fieldValue not in variableToPlot.keys():
                    descriptorsAtThisLevel = []
                    descriptorsAtThisLevel.append(uid)
                    variableToPlot[fieldValue] = descriptorsAtThisLevel
                else:
                    variableToPlot[fieldValue].append(uid)

    fileName = field + "_percentages_" + str(min(yearsConsidered)) + "-" + str(max(yearsConsidered))
    if not deserialize :
        fileName = fileName + "_notDeserialized"
    fileName = fileName + ".png"
    # Get Restriction Level
    if FGDiseaseOnly:
        fileName = "FGD_" + fileName;
    if not provLevelPrefix == None:
        fileName = provLevelPrefix + "_" + fileName;
    label = field

    totalDF = pd.DataFrame();
    rlKeys = []
    rlCounts = []
    for key in variableToPlot.keys():
        # print(key, len(restrictionLevels[key]))
        rlKeys.append(key)
        rlCounts.append(len(variableToPlot[key]))

    total = sum(rlCounts)
    rlKeys.append("total")
    rlCounts.append(total)

    rlCountPercentages = []
    for i in rlCounts:
        rlCountPercentages.append(i / total)
    totalDF[label] = rlKeys
    totalDF["Count"] = rlCounts
    totalDF["Percentage"] = rlCountPercentages
    totalDF = totalDF.sort_values(by=[label])

    # print(totalDF)
    totalDF.to_csv(path + fileName + ".csv", ";")

    # plt.show()
    # figure.clear()
    # cor = plotDF.corr(method='spearman')# Spearman rank correlation
    # sns.heatmap(cor, annot=True, cmap=plt.cm.RdBu)
    # figure.suptitle('Spearman rank correlation ' + title , fontsize=15)
    # figure.savefig(path + "Spearman_" + fileName)
    # plt.show()

