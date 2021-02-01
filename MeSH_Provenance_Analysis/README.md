# MeSH_Provenance_Analysis

Python project for processing and analyzing the CSV files produced by the *MeSH_Provenance_Harvesting* project, to produce overall statistics and diagrams, such as bar charts and line charts of the distribution of new descriptors through the years.    


## Requirements
The script is written in Python 3.6.

All libraries and versions required are listed in requirements.txt.

This project has been developed in PyCharm 2018.1.2

## How to use

### Configure
 Update the configurations in /settings.yaml
 
* workingPath: The path for the folder where all reslts will be stored (e.g. 'D:\\2005_2020_results')
* csvPath: The path to the folder with the CSV files from the excecution of the MeSH_Harvesting project (e.g. 'D:\\2005_2020_results')
* lastYear: The last year to consider in the analysis
* firstYear: The first year to consider in the analysis 
* splitChar: The character for joining/splitting serialized information (should be the same used in MeSH_Harvesting settings)

### Run
The main script is CalculateStats.py and considers no arguments. 
The execution leads into creating in the workingPath a CSV file with all the new descriptors identified for all years, as well as additional figures and CSV files with statistics and diagrams about them. 

Example call:

> python3.6 CalculateStats.py


## Reference

[1] Nentidis, A., Krithara, A., Tsoumakas, G., & Paliouras, G. (2021). What is all this new MeSH about? Exploring the semantic provenance of new descriptors in the MeSH thesaurus. https://arxiv.org/abs/2101.08293
