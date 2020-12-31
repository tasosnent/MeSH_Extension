# MeSH_Extension
## Exploring the conceptual provenance of new descriptors introduced during the extension of the MeSH thesaurus 


In this work [1], a conceptual framework is introduced for exploring where do new Medical Subject Headings (MeSH) descriptors come from. In particular, the notion of a Previous Host (PH) is defined, as an older MeSH descriptor that used to cover the topic of a new MeSH descriptor, prior to its introduction in the MeSH thesaurus. In this context, new descriptors are annotated with *provenance codes*, grouping the new descriptors into *provenance categories* representing how their PHs can be identified, and *provenance types*, describing their current relationship with their PHs in the MeSH hierarchy. 
This repository includes the source code and the results of a study [1] on the conceptual provenance of new descriptors in the MeSH thesaurus. 

In particular, it includes:
1. The **MeSH_Provenance_Harvesting** Java project for identifying the new descriptors by comparing different versions of MeSH and annotating them with provenance codes. This project produces a CSV file for each year in the period of study, including all the new descriptors introduced in this year, annotated with *provenance codes*, as well as other related information (the PHs, the parents in the MeSH hierarchy, the MeSH categories, etc).
2. The **MeSH_Provenance_Analysis** Python project for processing and analyzing the CSV files produced by the *MeSH_Provenance_Harvesting* project, to produce overall statistics and diagrams, such as bar charts and line charts of the distribution of new descriptors through the years.    
3. The **NewDescriptors_2006_2020.csv** file with the 6,915 new descriptors introduced in MeSH between 2006 and 2020, annotated with provenance codes and other relevant information. The reference year considered for provenance type calculation is 2020.
 * The columns in this file are:
    * Prov. Code	:	The provenance code combining the provenance category and provenance type of the descriptor.
    * Prov. Category	:	The provenance category of the descriptor representing how its PHs have been identified.
    * Prov. Type	:	The provenance type of the descriptor representing the current relationship of the descriptor with its PHs.
    * Conc. Rel.	:	For category 1 descriptors, that used to be subordinate concepts of a PH, this field stores the relation of the subordinate concept to the preferred concept of the PH.
    * Descr. UI	:	The unique identifier of the descriptor in MeSH.
    * Descr. Name	:	The preferred label of the descriptor in MeSH.
    * PH count	:	The number of PHs identified for the descriptor.
    * #Parent Ds	:	The number of parent descriptors identified for the descriptor in the current hierarchy.
    * PHs	:	The PHs identified for the descriptor.
    * #Descendant Ds	:	The number of descendants of this descriptor in the current MeSH hierarchy.
    * MeSH Categories	:	The MeSH categories of the descriptor
    * year	:	The year of introduction of the descriptor.

## Reference

[1] Nentidis, A., Krithara, A., Tsoumakas, G., & Paliouras, G. (2020). What is all this new MeSH about? Exploring the semantic provenance of new descriptors in the MeSH thesaurus. 
