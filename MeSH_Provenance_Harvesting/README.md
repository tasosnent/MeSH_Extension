# Harvesting MeSH Versions

The **MeSH_Provenance_Harvesting** Java project for identifying the new descriptors by comparing different versions of MeSH and annotating them with provenance codes. This project produces a CSV file for each year in the period of study, including all the new descriptors introduced in this year, annotated with *provenance codes*, as well as other related information (the PHs, the parents in the MeSH hierarchy, the MeSH categories, etc)

## Requirements
java 1.8.0_91

All required libraries are listed in requirements.txt

This project has been developed in NetBeans IDE 8.1

## How to use

### Configure
 Update the configurations in /settings.yaml
 
* workingPath: The path for the folder where all reslts will be stored (e.g. 'D:\\2005_2020_results')
* meshXmlPath: The path to the flder with the XML files for all MeSH versions (both descXXXX.xml and suppXXXX.xml should be available) (e.g. 'D:\\MeSH_All')
* suggestPMNmappings: If True, suggest mappings from new descriptors to SCRs (based on PMN fields etc) for manual review.
* calculateConceptAVGs: If True, calculate average No of concepts per SCR and Descriptor and print in the log for each year.
* debugMode: If True, additional information will also be printed in the log
* nowYear: The last year to consider in the analysis, also considered the reference year for provenance type calculation
* oldYearInitial: The first year to consider in the analysis
* splitChar: The character for joining/splitting serialized information

### Run
The main method is in MeSHDiffHarvester.java and considers no arguments. 
The execution leads into creating in the workingPath a CSV file for each year, except for the very first, containined the new descriptors (e.g. newDescriptors_2010.csv). 
A log file is also created in the workingPath with information about the process of the harvesting. This log file also holda additional information when suggestPMNmappings, calculateConceptAVGs, and debugMode are enabled.

## Reference

[1] Nentidis, A., Krithara, A., Tsoumakas, G., & Paliouras, G. (2021). What is all this new MeSH about? Exploring the semantic provenance of new descriptors in the MeSH thesaurus. https://arxiv.org/abs/2101.08293
