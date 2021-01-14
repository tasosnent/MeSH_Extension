
package DiffHarvester;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import help.Helper;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.json.simple.JSONArray;
import org.xml.sax.SAXException;
import org.apache.commons.text.similarity.LevenshteinDistance;
import yamlSettings.Settings;

/** 
 * A Harvester for different versions of MeSH
 * @author tasosnent
 */
public class MeSHDiffHarvester {
    private static Settings s;
    private static String pathDelimiter;    // The delimiter in this system (i.e. "\\" for Windows, "/" for Unix)
    private static boolean suggestPMNmappings; //Flag, whether to sugggest additional PMN mappings based on terms, names etc or not.
    private static boolean calculateConceptAVGs; //Flag, whether to sugggest additional PMN mappings based on terms, names etc or not.
    private static boolean debugMode; //Flag, whether to sugggest additional PMN mappings based on terms, names etc or not.

    private String experimentFolder;    // The folder for experiment files
    private String dateNow;
    private String inputFolder;
    private String XMLfileNow; 
    private ArrayList <Descriptor> descriptorsNow; // Descriptor objects
    private HashMap <String, Descriptor> descriptorsNowMap; // DUI -> Descriptor object
    private HashMap <String, Descriptor> treeNumbers; // tree number -> Descriptor object
    private HashMap <String, Descriptor> descriptorNowTerms; // DescriptorName -> Descriptor object
    private static HashMap <String, String> SCRexceptions; // descriptor ID -> SCR ID (of SCRs manually identified as sceptions)
    
    /**
     * Characters to consider when comparing terms for quasi-exact match
     */
    public static String re= "[^a-zA-Z0-9]"; // Characters to consider when comparing terms for quasi-exact match
    public static String splitChar = "-"; // The character for joining/splitting serialized information

    /**
      * Initialize a Harvester for different versions of MeSH
     * @param experimentFolder      Path to a folder to store all the experiment files
     * @param inputFolder           Path to the folder with MeSH XML files to be harvested and compared
     * @param nowDate               The year of the reference MeSH version for provenance type calculation
     */
    MeSHDiffHarvester(String experimentFolder, String inputFolder, String nowDate){
        MeSHDiffHarvester.pathDelimiter = File.separator;
        this.inputFolder = inputFolder;
        this.experimentFolder = experimentFolder;
        this.dateNow = nowDate;     

        // The MeSH XML files to be harvested and compared
        XMLfileNow = inputFolder+"\\desc"+nowDate+".xml";       
            
        // Harvest current XML version
        this.descriptorsNow = loadDescriptors(this.XMLfileNow);
        this.descriptorsNowMap = new HashMap <> ();
        this.descriptorNowTerms = new HashMap <> ();
                
        // Update the Hierarchical position of each Descriptor now
        this.treeNumbers = new HashMap <> ();
        for(Descriptor d : this.descriptorsNow){
            this.descriptorsNowMap.put(d.getDescriptorUI(), d);
            for(String treeNumber : d.getTreeNumbers()){
                this.treeNumbers.put(treeNumber, d);
            }     
            for(String term : d.getTerms()){
                this.descriptorNowTerms.put(term, d);
            }     
        }     
        
        if (SCRexceptions == null){
            SCRexceptions = getSCRexceptionMap();
        }
    }
    
    /**
     * Perform analysis of the 15 versions of MeSH from 2006 to 2020.
     * @param args 
     */
    public static void main(String[] args) {    
            
        /*  Initialize  */
        s = new Settings("settings.yaml");
        String workingPath = (String)s.getProperty("workingPath");
        String meshXmlPath = (String)s.getProperty("meshXmlPath");
        suggestPMNmappings = (Boolean)s.getProperty("suggestPMNmappings");
        calculateConceptAVGs = (Boolean)s.getProperty("calculateConceptAVGs");
        debugMode = (Boolean)s.getProperty("debugMode");
        splitChar = (String)s.getProperty("splitChar");
                
        int nowYear = (Integer)s.getProperty("nowYear"); // Both the reference year and the last year to consider.
        int oldYearInitial = (Integer)s.getProperty("oldYearInitial");
        
        /**     Statistics on new Descriptors    **/    

        // Log printing setup
        String nowDate = new Date().toString();   
        nowDate = nowDate.replace(":", "-");
        nowDate = nowDate.replace(" ", "_");
        Helper.createFolder(workingPath);
        String logFile = workingPath + "\\javalog_"+nowDate+".txt";
        try {
            System.setOut(new PrintStream(logFile));
        } catch (FileNotFoundException ex) {
            System.out.println(" " + new Date().toString() + " problem setting " + logFile + " as log file : " + ex.getMessage());
        }    
        
        // Create the harvester for the given reference MeSH version
        MeSHDiffHarvester harvester = new MeSHDiffHarvester(workingPath, meshXmlPath, String.valueOf(nowYear));
        
        Date start = new Date();      
        String descr = " time in";
        /***    Statistics on Provenance Codes, without documents and labels     ***/    
        initialStats(harvester, oldYearInitial, nowYear);
        descr += " initialStats ";        
        Date end = new Date();
        Helper.printTime(start, end, descr + " from " + oldYearInitial + " until " + nowYear);
        
    }

    /**
     * Get statistics on Provenance Codes based on MeSH XML files 
     *      Without any document and label statistics
     * @param harvester         A MeSHDiffHarvester object
     * @param oldYearInitial    The first year to consider
     * @param nowYear           The current year used as reference and as the last year to consider
     */ 
    public static void initialStats(MeSHDiffHarvester harvester, int oldYearInitial, int nowYear ){
        for(int year = oldYearInitial; year < nowYear; year ++ ){
            String oldYear = String.valueOf(year);
            String newYear = String.valueOf(year+1);    
            System.out.println(" " + new Date().toString() + " Run for (" + oldYear + " - " + newYear + ")");
            /*  Find new descriptors and save them in a SumUp CSV file  */
            ArrayList <Descriptor> descriptorsDiff = harvester.getNewDescriptors(oldYear, newYear);

            try {
                harvester.StatsOnDescriptors(descriptorsDiff, oldYear, newYear);
            } catch (IOException ex) {
                Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
       
    /*  Application Tasks */

    /**
     * Create a CSV file with all new descriptors introduced between the two years 
     *      Store in a corresponding experiment folder 
     *      
     * @param descriptorsDiff   A set of descriptors
     * @param oldDate           A start date
     * @param newDate           An end date
     * @throws IOException 
     */
    public void StatsOnDescriptors(ArrayList <Descriptor> descriptorsDiff, String oldDate, String newDate) throws IOException{
        String curentExperimentFolder = getCurrentExperimentFolder(oldDate,newDate);
        Helper.createFolder(curentExperimentFolder);

        String totalCSV = this.getIntialCSVFile(oldDate, newDate);
        // Use-case properties to be reported in the CSV files
        try (CSVWriter csvWriterTotal = newCSVWriter(totalCSV)) {
            // Use-case properties to be reported in the CSV files
            DescriptorIntroduction.writeTitlesInCSV(csvWriterTotal); 
            
            DescriptorIntroduction useCase;
            //For each new descriptor retrieve/caclulate required numbers and info
            for(Descriptor d : descriptorsDiff){
                useCase = new DescriptorIntroduction(d, this);
                
                // Write any use case for stats
                useCase.writeInCSV(csvWriterTotal);
                csvWriterTotal.flush();
                
            } // any new descriptor introduced, still available Now
        }         
    }                   
    
    /*  MeSH Descriptor handling functionality   */   
    
    /**
     * A hard-coded list of manually checked exceptional cases of SCRs that do not follow the assumptions necessary for automated identification of the SCR and PHs.
     * @return HashMap <String, String> list of exceptions: descriptor ID -> SCR ID
     */
    public static HashMap <String, String> getSCRexceptionMap(){
        HashMap <String, String> exceptions = new HashMap <String, String> (); // descriptor ID -> SCR ID
        exceptions.put("D050482", "C022796"); // Catecholamine Plasma Membrane Transport Proteins(D050482) -> catecholamine transport protein > catecholamine transport protein(C022796)
        exceptions.put("D050961", "C064524"); // Glycine Decarboxylase Complex H-Protein(D050961) -> glycine decarboxylase complex > glycine dehydrogenase (decarboxylating)(C064524)
        exceptions.put("D051037", "C082071"); // Large-Conductance Calcium-Activated Potassium Channel alpha Subunits(D051037) -> large-conductance calcium-activated potassium channels, alpha subunit > large-conductance calcium-activated potassium channels(C082071)
        exceptions.put("D051038", "C082071"); // Large-Conductance Calcium-Activated Potassium Channel beta Subunits(D051038) -> large-conductance calcium-activated potassium channels, beta subunits > large-conductance calcium-activated potassium channels(C082071)
        exceptions.put("D051150", "C027219"); // Methionine Sulfoxide Reductases(D051150) -> methionine sulfoxide reductase(C027219)
        exceptions.put("D058265", "C093200"); // Receptors, Adrenomedullin(D058265) ->receptor, adrenomedullin > adrenomedullin receptor(C093200)
        exceptions.put("D058286", "C112409"); // Calcitonin Receptor-Like Protein(D058286) -> calcitonin receptor-like protein, human/mouse/rat > calcitonin receptor-like receptor(C112409)[D018003]
        exceptions.put("D053502", "C062216"); // Arsenate Reductases(D053502) -> arsenate reductase > arsenite-transporting ATPase(C062216)[D009097, D016623]
        exceptions.put("D053513", "C087895"); // Matrix Metalloproteinase 16(D053513) ->  Matrix metalloproteinase 16, mouse/(membrane-inserted) protein, human > membrane-type matrix metalloproteinase(C087895)
        exceptions.put("D053515", "C498537"); // Matrix Metalloproteinase 17(D053515) -> matrix metalloproteinase 17 protein, mouse/(membrane-inserted) protein, human > Mmp17 protein, mouse(C498537)[D020782]
        exceptions.put("D053724", "C108669"); // Receptors, Interleukin-18(D053724) -> receptor, interleukin-18 > interleukin-18 receptor(C108669)[D018123]
        exceptions.put("D054856", "C088654"); // G-Quadruplexes(D054856) -> quadruplex DNA > quadruplex DNA(C088654)[D004247]
        exceptions.put("D055394", "C120559"); // Tolloid-Like Metalloproteinases(D055394) -> tolloid metalloproteinase > tolloid metalloproteinase(C120559)[D019485, D045726]
        exceptions.put("D055533", "C091430"); // Survival of Motor Neuron 1 Protein(D055533) ->  Survival motor neuron 1 protein > SMN protein (spinal muscular atrophy)(C091430)[D009419, D016601, D017362]
        exceptions.put("D055540", "C091430"); // Survival of Motor Neuron 2 Protein(D055540) -> Survival motor neuron 2 protein > SMN protein (spinal muscular atrophy)(C091430)[D009419, D016601, D017362]
        exceptions.put("D055650", "C087489"); // NK Cell Lectin-Like Receptor Subfamily A(D055650)-> killer cell lectin-like receptor, subfamily A(C087489)[D000950, D037181]
        exceptions.put("D056604", "C431250"); // Grape Seed Extract(D056604) -> IH636 grape seed proanthocyanidin extract > IH636 grape seed proanthocyanidin extract(C431250)[D010936, D044945]
        exceptions.put("D056931", "C063074"); // Cryptochromes(D056931) -> cryptochrome > cryptochrome(C063074)[D005420]
        exceptions.put("D052218", "C081465"); // Fanconi Anemia Complementation Group C Protein(D052218) -> Fanconi anemia, complementation group C protein, human/rat > Fanconi anemia proteins(C081465)[D004268, D009687, D018797]
        exceptions.put("D055458", "C099088"); // Left-Right Determination Factors(D055458) -> Left-right determination factor B > left-right determination factor proteins(C099088)[D016212]
        exceptions.put("D058261", "C112410"); // Receptor Activity-Modifying Proteins(D058261) -> receptor-activity-modifying protein > receptor-activity-modifying protein(C112410)[D008565, D047908]
        exceptions.put("D058309", "C101058"); // Receptors, Prostaglandin E, EP4 Subtype(D058309) -> receptor, prostaglandin EP4 > prostaglandin E2 receptor, EP4 subtype(C101058)[D018078]
        exceptions.put("D058925", "C419189"); // Receptors, Purinergic P2Y12(D058925) -> purinoceptor P2Y12 > purinoceptor P2Y12(C419189)[D018048]
        exceptions.put("D058927", "C062611"); // 5-Lipoxygenase-Activating Proteins(D058927) -> 5-lipoxygenase-activating protein > 5-lipoxygenase-activating protein(C062611)[D002352, D008565]
        exceptions.put("D059912", "C048883"); // HLA-B37 Antigen(D059912) -> HLA-B37 > HLA-B37(C048883)[D015235]
        exceptions.put("D050609", "C051381"); // Sodium-Phosphate Cotransporter Proteins, Type I(D050609) -> type I sodium-phosphate cotransporter proteins > sodium-phosphate cotransporter proteins(C051381)[D027981]
        exceptions.put("D050613", "C051381"); // Sodium-Phosphate Cotransporter Proteins, Type IIa(D050613) -> type IIa sodium-phosphate cotransporter proteins > sodium-phosphate cotransporter proteins(C051381)[D027981]
        exceptions.put("D050614", "C051381"); // Sodium-Phosphate Cotransporter Proteins, Type IIb(D050614) -> sodium-phosphate cotransporter proteins(C051381)[D027981]
        exceptions.put("D050615", "C051381"); // Sodium-Phosphate Cotransporter Proteins, Type IIc(D050615) -> sodium-phosphate cotransporter proteins(C051381)[D027981]
        exceptions.put("D051237", "C065684"); // Receptors, Pituitary Adenylate Cyclase-Activating Polypeptide, Type I(D051237) -> pituitary adenylate cyclase-activating peptide receptor, type I > PACAP receptors(C065684)[D011956]
        exceptions.put("D051903", "C079496"); // Nuclear Factor 45 Protein(D051903) -> nuclear factor of activated T-cells, 45-kDa protein, human > nuclear factors of activated T-cells(C079496)[D004268, D009687, D014157]
        exceptions.put("D060285", "C069865"); // Receptors, Autocrine Motility Factor(D060285) -> autocrine motility factor receptor(C069865)[D018121, D044767]
        exceptions.put("D062948", "C066667"); // rho Guanine Nucleotide Dissociation Inhibitor alpha(D062948) ->  rho guanine nucleotide dissociation inhibitors(C066667)[D020730]
        exceptions.put("D064026", "C050555"); // Calbindins(D064026) -> calbindin(C050555)[D002134]
        exceptions.put("D064173", "C071997"); // Anaphase-Promoting Complex-Cyclosome(D064173) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064189", "C071997"); // Apc1 Subunit, Anaphase-Promoting Complex-Cyclosome(D064189) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064190", "C071997"); // Apc2 Subunit, Anaphase-Promoting Complex-Cyclosome(D064190) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064191", "C071997"); // Apc3 Subunit, Anaphase-Promoting Complex-Cyclosome(D064191) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064192", "C071997"); // Apc4 Subunit, Anaphase-Promoting Complex-Cyclosome(D064192) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064193", "C071997"); // Apc5 Subunit, Anaphase-Promoting Complex-Cyclosome(D064193) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064194", "C071997"); // Apc6 Subunit, Anaphase-Promoting Complex-Cyclosome(D064194) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064195", "C071997"); // Apc7 Subunit, Anaphase-Promoting Complex-Cyclosome(D064195) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064196", "C071997"); // Apc8 Subunit, Anaphase-Promoting Complex-Cyclosome(D064196) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064197", "C071997"); // Apc10 Subunit, Anaphase-Promoting Complex-Cyclosome(D064197) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064198", "C071997"); // Apc11 Subunit, Anaphase-Promoting Complex-Cyclosome(D064198) -> anaphase-promoting complex(C071997)[D043743]
        exceptions.put("D064199", "C486219"); // Cdc20 Proteins(D064199) -> Cdc20 protein, rat(C486219)[D018797] 
        exceptions.put("D064249", "C107343"); // Securin(D064249) -> pituitary tumor-transforming proteins(C107343)[D009363]
        exceptions.put("D064466", "C422027"); // Solute Carrier Family 12, Member 1(D064466) -> solute carrier family 12, member 1 protein, mouse > sodium-potassium chloride cotransporter 2 protein(C422027)[D028021]
        exceptions.put("D064570", "C109791"); // Ubiquitin-Specific Proteases(D064570) -> ubiquitin-specific protease(C109791)[D010450]
        exceptions.put("D065668", "C021392"); // Vitamin D3 24-Hydroxylase(D065668) -> vitamin D 24-hydroxylase(C021392)[D013250]
        exceptions.put("D000067616", "C090441"); // S100A12 Protein(D000067616) -> S100A12 protein, human(C090441)[D009418]
        exceptions.put("D000067736", "C503889"); // Ataxin-10(D000067736) -> ATXN10 protein, human(C503889)[D009419]
        exceptions.put("D000067758", "C578749"); // Glucagon-Like Peptide-2 Receptor(D000067758) -> GLP2R protein, human(C578749)[D018027]
        exceptions.put("D000068857", "C512720"); // Human Papillomavirus Recombinant Vaccine Quadrivalent, Types 6, 11, 16, 18(D000068857) -> human papillomavirus vaccine L1, type 6,11,16,18(C512720)[D053918]
        exceptions.put("D000069479", "C510748"); // Buprenorphine, Naloxone Drug Combination(D000069479) -> suboxone(C510748)[D002047, D009270]
        exceptions.put("D000071164", "C072090"); // Trefoil Factor-2(D000071164) -> trefoil factor(C072090)[D010455]
        exceptions.put("D000071165", "C082495"); // Trefoil Factor-3(D000071165) -> TFF3 protein, human(C082495)[D010455]
        exceptions.put("D000071221", "C578253"); // Enhancer of Zeste Homolog 2 Protein(D000071221) -> EZH2 protein, rat(C578253)[D063151]
        exceptions.put("D000071518", "C469720"); // Liver X Receptors(D000071518) -> liver X receptor(C469720)[D057093]
        exceptions.put("D000071557", "C074198"); // beta-Arrestins(D000071557) -> beta-arrestin(C074198)[D019390]
        exceptions.put("D000071616", "C087456"); // CDX2 Transcription Factor(D000071616) -> Cdx-2-3 protein(C087456)[D015534, D018398]
        exceptions.put("D000071797", "C031520"); // Butyrophilins(D000071797) -> butyrophilin(C031520)[D008562]
        exceptions.put("D000071876", "C022467"); // Prostaglandin-E Synthases(D000071876) -> prostaglandin-E synthase(C022467)[D019746]
        exceptions.put("D000072101", "C066820"); // Jagged-2 Protein(D000072101) -> Serrate proteins(C066820)[D002135, D008565, D036341]
        exceptions.put("D000072396", "C064367"); // Endothelin-Converting Enzymes(D000072396) -> endothelin-converting enzyme(C064367)[D008666, D016282]
        exceptions.put("D000072621", "C098989"); // Peptidyl-Prolyl Cis-Trans Isomerase NIMA-Interacting 4(D000072621) -> NIMA-interacting peptidylprolyl isomerase(C098989)[D019696]
        exceptions.put("D000073718", "C072099"); // Pancreatitis-Associated Proteins(D000073718) ->  pancreatitis-associated protein(C072099)[D000951, D014408, D037181]        
        exceptions.put("D064006", "C020086"); // Dodecenoyl-CoA Isomerase(D064006) -> dodecenoyl-coenzyme A delta-isomerase(C020086)[D019748]
        exceptions.put("D000067776", "C533319"); // RNA Recognition Motif Proteins(D000067776) -> RNA binding motif protein 3, rat > RBM3 protein, rat(C533319)[D016601]
        exceptions.put("D000072081", "C099744"); // Patched-1 Receptor(D000072081) -> patched receptors(C099744)[D011956]
        exceptions.put("D000072082", "C099744"); // Patched-2 Receptor(D000072082) -> patched receptors(C099744)[D011956]
        exceptions.put("D000072379", "C071420"); // NIMA-Related Kinases(D000072379) -> NIMA protein kinase(C071420)[D017346, D018797]
        exceptions.put("D000075224", "C562490"); // Cardiac Conduction System Disease(D000075224) -> Cardiac Conduction Defect(C562490)[D053840]
        exceptions.put("D000076002", "C021373"); // Hyaluronan Synthases(D000076002) -> hyaluronan synthase(C021373)[D014453]
        exceptions.put("D000076245", "C439767"); // Heterogeneous Nuclear Ribonucleoprotein A1(D000076245) -> hnRNP A1(C439767)[D034481]

//        Not matching PHs (or not fully-matching PHs)
        exceptions.put("D000067699", "C092341"); // Ataxin-3(D000067699) -> ataxin-3 protein, human > ATXN3 protein, human(C092341)[D009419, D009687, D012097]
        exceptions.put("D000080866", "C076066"); // Nucleobindins(D000080866) -> nucleobindin(C076066)[D002135, D004268, D009419] 
        // One of multiple equivalent SCRs (i.e. leading to the same PH set)
        exceptions.put("D000067596", "C506536"); // Interleukin-33(D000067596) -> IL33 protein, human(C506536)[D007378]
        exceptions.put("D000070796", "C038196"); // Perilipin-3(D000070796) -> perilipin 3 protein, human > PLIN3 protein, human(C038196)[D033921]
        
        exceptions.put("D053422", "C112394"); // Receptor-Interacting Protein Serine-Threonine Kinases(D053422) -> receptor-interacting serine-threonine kinase 2 > receptor-interacting serine-threonine kinase 2(C112394)[D017346, D047988]
        exceptions.put("D053487", "C115600"); // DEAD-box RNA Helicases(D053487) -> DEAD-box RNA helicase Y, mouse > Ddx3y protein, mouse(C115600)[D020365]
        exceptions.put("D053663", "C415369"); // Interleukin-13 Receptor alpha2 Subunit(D053663) -> interleukin 22 receptor, alpha 2 chain > interleukin-22 receptor(C415369)[D018123]
        exceptions.put("D054342", "C108232"); // Receptors, KIR2DL1(D054342) -> KIR receptors(C108232)[D011971]
        exceptions.put("D054343", "C108232"); // Receptors, KIR2DL2(D054343) -> KIR receptors(C108232)[D011971]
        exceptions.put("D054344", "C108232"); // Receptors, KIR2DL3(D054344) -> KIR receptors(C108232)[D011971]
        exceptions.put("D054345", "C108232"); // Receptors, KIR2DL4(D054345) -> KIR receptors(C108232)[D011971]
        exceptions.put("D054347", "C108232"); // Receptors, KIR3DL2(D054347) -> p140-KIR3DL2 inhibitory receptor > KIR receptors(C108232)[D011971]
        exceptions.put("D054349", "C108073"); // Receptors, KIR3DS1(D054349) -> receptor, LAIR1 > leukocyte-associated immunoglobulin-like receptor 1(C108073)[D011971]
        exceptions.put("D055655", "C079062"); // NK Cell Lectin-Like Receptor Subfamily K(D055655) -> NKG2B receptor > natural killer cell activating receptors(C079062)[D011971]
        exceptions.put("D064486", "C498265"); // Solute Carrier Family 12, Member 3(D064486) -> solute carrier family 12, member 3 protein, mouse > Slc12a3 protein, mouse(C498265)[D011955, D027981]
        exceptions.put("D000074923", "C575914"); // High-Temperature Requirement A Serine Peptidase 2(D000074923) -> high-temperature requirement protein A1, rat > HtrA1 protein, rat(C575914)[D012697]
        exceptions.put("D000077293", "C094515"); // Receptor, Transforming Growth Factor-beta Type I(D000077293) -> Xenopus transforming growth factor beta-related receptor type I > XTrR-I receptor(C094515)[D018125] 
//        exceptions.put("", ""); // 
        return exceptions;
    }

    /**
     * Print Descriptors Mapped for manual cross-checking with the PMnote
     *      Only used in debugMode
     * @param oldSCR 
     */
    public void printDescriptorsMapped(SCR oldSCR){
        ArrayList<String> dUIs = oldSCR.getDescriptorUIs();
           for(String dui : dUIs){
                 String tmp = dui;
                 if(descriptorsNowMap.containsKey(dui)){
                     tmp += " " + descriptorsNowMap.get(dui);
                 }
                 System.out.println("\t\t\t > " + tmp);
           }
    }
    
    /**
     * Get the parts of a term for term-part matching
     *      Only used in debugMode
     * @param term The term to analyze
     * @return  ArrayList of Strings termToFindParts 
     */
    public ArrayList <String> getTermParts(String term){
        ArrayList <String> termParts = new ArrayList <String> ();
        String[] partsRaw = term.trim().toLowerCase().split(re);
        for(String part : partsRaw){
            part = part.trim();
            if(!part.equals("")){
                termParts.add(part);
            }
        }
        return termParts;
    }    
    
    /**
     * Check whether the given descriptor (actually its preferred concept) corresponds to a previous SCR
     * @param d The descriptor to consider
     * @param conceptUI2SCROld  HashMap <String, SCR> Concept UI to corresponding SCR for the previous year SCR set
     * @param term2SCROld       HashMap <String, SCR> Concept Term (normalized) to corresponding SCR for the previous year SCR set
     * @param termRaw2SCROld    HashMap <String, SCR> Concept Term (raw) to corresponding SCR for the previous year SCR set
     * @param SCRsOld           HashMap <String, SCR> SCR UI to corresponding SCR for the previous year SCR set
     * @return the corresponding SCR
     */    
    public SCR extractSCR(Descriptor d, HashMap <String, SCR> conceptUI2SCROld, HashMap <String, SCR> term2SCROld, HashMap <String, SCR> termRaw2SCROld, HashMap <String, SCR> SCRsOld){
        // Hardcoded expections not catched by the regular expressions
        // SCRs for these descriptors have been proposed (printed) by this script, manually reviewed and added in this list of exeptions to avoid manually checking them again.
        
        SCR oldSCR = null;
        String preferredConceptUI = d.getPreferredConceptUI();
        // Descriptor PubLicMeSHNote for checking the special case of SCR descriptrs folowing the "X  was indexed under Y" pattern.
        String pmNote = d.getPublicMeSHNote();
        PMnote note = new PMnote(pmNote);
        if(debugMode){            
            System.out.println("\t D: " + d);
            System.out.println("\t\tPMnote: " + pmNote);
            System.out.println("\t\t" + note);
         }
       if(conceptUI2SCROld.containsKey(preferredConceptUI)) {
                // This is an old SCR concept upgraded into Descriptor
                oldSCR = conceptUI2SCROld.get(preferredConceptUI);
                // Check consistency with PMnote
                if(debugMode){
                    System.out.println("\t\t SCR found by pref Concept id:" + preferredConceptUI);
                
                    if(note.patternAmenable()){
                        System.out.println("\t\t\t ConceptId+pattern" + preferredConceptUI);
                    }                
                    // Print Descriptors for checking
                    printDescriptorsMapped(oldSCR);
                }
            } else if(note.patternAmenable()){ // PMnote not null and containing "X was indexed after y"
                String foundByName = null; // Whether the old SCR has been found by the current Descriptor name
                String foundByTerm = null; // Whether the old SCR has been found by the term extracted from PMnote
                // This is an old SCR concept upgraded into Descriptor with different concept ID
                // Check for SCR based on PMnote parsed SCR term
                if(note.termFound()){
                    String termRaw = note.getScrTerm();
                    String term = normalizeTerm(termRaw);
                    if(term2SCROld.containsKey(term)){
                        // SCR found by MeSH note parsed term
                        if(debugMode){
                            System.out.println("\t\t SCR found by term: " + term);
                        }
                        oldSCR = term2SCROld.get(term);
                        foundByTerm = oldSCR.getSCRUI();
                        if(debugMode){
                            System.out.println("\t\t SCR : " + oldSCR);
                     
                            // Print Descriptors for checking
                            printDescriptorsMapped(oldSCR);
                        }
                    } 
                } // else no term found

                // Check for SCR based on new descriptor name
                String dName = normalizeTerm(d.getDescriptorName());
                String dUI = d.getDescriptorUI();
                if(term2SCROld.containsKey(dName)){
                    // SCR found by current Descriptor term
                    oldSCR = term2SCROld.get(dName);
                    foundByName = oldSCR.getSCRUI();
                    if(debugMode){
                        System.out.println("\t\t SCR found by dName: " + dName);
                        System.out.println("\t\t SCR : " + oldSCR);
                        // Print Descriptors for checking
                        printDescriptorsMapped(oldSCR);    
                    }
                     
                } 
                if(SCRexceptions.containsKey(dUI)){
                // Check for SCR based on manually checked hardcoded exception list
                    // This is a manually checked exeptional case, assign the manually selected SCR
                    if(debugMode){
                        System.out.println("\t\t SCR found by exception list: " + dUI);
                    }
                    String oldSCRUI = SCRexceptions.get(dUI);
                    if(SCRsOld.containsKey(oldSCRUI)){
                        oldSCR = SCRsOld.get(oldSCRUI);
                        if(debugMode){
                            System.out.println("\t\t SCR : " + oldSCR);                            
    //                            System.out.println("\t\t\t oldSCRUI > " + oldSCRUI);
    //                            System.out.println("\t\t\t conceptUI2SCROld.contains(oldSCRUI) > " + conceptUI2SCROld.containsKey(oldSCRUI));
    //                            System.out.println("\t\t\t conceptUI2SCROld.get(oldSCRUI) > " + conceptUI2SCROld.get(oldSCRUI));
                            // Print Descriptors for checking
                            printDescriptorsMapped(oldSCR); 
                        }
                    } else { // Wrong exception rule. The mapped SCR UI is not included in the previous SCR version
                        System.out.println("\t\t\t Error : Wrong exception rule. The mapped SCR UI ("+oldSCRUI+") is not included in the previous SCR version " );
                    }                       
                } 
                if(foundByName != null && foundByTerm != null){
                    if(debugMode){
                        System.out.println("\t\t SCR found by both (dName & term) : " + dName);
                        if(!foundByName.equals(foundByTerm)){
                            System.out.println("\t\t\t dName & term contradiction : " + foundByName + " " + foundByTerm);                       
                        }             
                    }
                }
                if(this.suggestPMNmappings && oldSCR == null){
                // Check for best-matching SCR based on term distances and term-part matching
                    // This is a "new exception" not included in the manually checked ones. 
                    // Process this case further and print details for manual checking inclusion in the exceptional case list.

                    //Check both PMnote term and dName for best match                     
                    ArrayList <String> termsToFind =  new ArrayList <String> ();
                    if(note.termFound()){
                        termsToFind.add(note.getScrTerm());
                    }
//                    if(!termRaw.trim().equalsIgnoreCase(d.getDescriptorName().trim())){
                    if(!termsToFind.contains(d.getDescriptorName())){
                        termsToFind.add(d.getDescriptorName());     
                    }
                    System.out.println("\t SCR not found");
                    System.out.println("\t\t terms: " + termsToFind);  

                    // Check for matching SCR for each term source (PMnote-parsed term or Descriptor name) independently
                    Iterator <String> termsToFindIt = termsToFind.iterator();
                    while(termsToFindIt.hasNext()){ 
                    // For each term (PMnote-parsed term or Descriptor name)
                        String currTermToFind = termsToFindIt.next();
                        ArrayList <String>  partMatchingCSRs = new ArrayList <String>  (); // Terms with exact part matching
                        HashMap <String, Integer> partMatchingCSRsWithAdditionalParts = new HashMap <String, Integer> (); // Terms with exact part matching
                        ArrayList <String> bestMatchingCSRs = new ArrayList <String> (); // Current best-matching term(s) based on string distance
                        int distance = Integer.MAX_VALUE; // Current string distance of best-matching term(s)

                        // Get term-parts
                        ArrayList <String> termToFindParts = getTermParts(currTermToFind);   
                        
                        // Check each SCR-term in the previous version
                        Iterator <String> SCRterms = termRaw2SCROld.keySet().iterator();
                        while(SCRterms.hasNext()){   
                        // for each SCR term 
                            String candidateSCRterm = SCRterms.next();

                            // ~*~ Step 1
                            // Check for term-part matching with all terms
                            ArrayList <String> candidateSCRtermParts = getTermParts(candidateSCRterm);
                            boolean allPartsFound = true; // Fals if any part of the term to find id not found in candidateSCRterm
                            
                            Iterator <String> partsToFindIt = termToFindParts.iterator();
                            while(partsToFindIt.hasNext() && allPartsFound){
                                String termPart = partsToFindIt.next();
                                if(!candidateSCRtermParts.contains(termPart)){
                                    allPartsFound = false;
                                } else {
                                    candidateSCRtermParts.remove(termPart);
                                }
                            }                                    
                            if(allPartsFound){
                               if(candidateSCRtermParts.isEmpty()){
                                    partMatchingCSRs.add(candidateSCRterm);
                                } else {
                                   // Additional termToFindParts exist
                                   partMatchingCSRsWithAdditionalParts.put(candidateSCRterm, candidateSCRtermParts.size());
                               }
                            } // Else, some termToFindParts not found with exact match
                                
                            // ~*~ Step 2
                            // Check for best-match based on distance
                            int currentDistance = new LevenshteinDistance().apply(normalizeTerm(currTermToFind), normalizeTerm(candidateSCRterm)) ;
                            if(currentDistance <= distance){
                                if(currentDistance < distance){
                                    bestMatchingCSRs.clear();
                                    distance = currentDistance;
                                }
                                bestMatchingCSRs.add(candidateSCRterm);
                            }
                        } // End iterating all SCR terms     

                        System.out.println("\t\t term: " + currTermToFind);               
                        System.out.println("\t\t termParts: " + termToFindParts);  

                        if(!partMatchingCSRs.isEmpty()){
                            System.out.println("\t\t\t All parts exact: " + partMatchingCSRs.size());   
                            for(String s : partMatchingCSRs){
                            System.out.println("\t\t\t\t " + s + " > " + termRaw2SCROld.get(s));   
                            }
                        }
                        if(!partMatchingCSRsWithAdditionalParts.isEmpty()){
                            System.out.println("\t\t\t with additional parts: " + partMatchingCSRsWithAdditionalParts.size());   
                             // trick to avoid printing too many alternatives
                            if(partMatchingCSRsWithAdditionalParts.size() <= 10){
                                for(String s : partMatchingCSRsWithAdditionalParts.keySet()){
                                    System.out.println("\t\t\t\t " + partMatchingCSRsWithAdditionalParts.get(s) + " : "+ s + " > " + termRaw2SCROld.get(s));
                                }   
                            } else {  // print only the alternatives with less additional parts
                                int minValue = Collections. min(partMatchingCSRsWithAdditionalParts.values());
                                for(String s : partMatchingCSRsWithAdditionalParts.keySet()){                                
                                    if(partMatchingCSRsWithAdditionalParts.get(s) == minValue){
                                        System.out.println("\t\t\t\t " + partMatchingCSRsWithAdditionalParts.get(s) + " : "+ s + " > " + termRaw2SCROld.get(s));                                        
                                    }
                                }
                            }
                        }
                        if(!bestMatchingCSRs.isEmpty()){
                            System.out.println("\t\t\t best match: " + bestMatchingCSRs.size());   
                            for(String s : bestMatchingCSRs){
                            System.out.println("\t\t\t\t " + s + " > " + termRaw2SCROld.get(s));   
                            System.out.println();
                            }
                        }
                    } // End foreach term
                } // End handling PMnote field for exception
            } // Else, PMnote missing or not containing "X was indexed after y" 
        if(debugMode){
            System.out.println();
        }
        return oldSCR;
    }
    
    /**
     * Create a Map from each concept unique id to corresponding descriptor for the given list of descriptors
     * @param ds    a list of descriptors to be considered
     * @return      HashMap <String,Descriptor> a Map from each concept unique id to corresponding descriptor for the given list of descriptors
     */
    public HashMap <String,Descriptor> getConceptUIToDescriptorMap(ArrayList <Descriptor> ds){
        HashMap <String,Descriptor> ConceptUIToDescriptor = new HashMap <> ();
        for(Descriptor d : ds){
            for(String c : d.getConceptUIs()){
                ConceptUIToDescriptor.put(c, d);
            }
        }
        return ConceptUIToDescriptor;        
    }
    
    /**
     * Harvests two versions of MeSH and returns a list of Descriptors added in the new version, not present in the previous
     * @param oldDate   the old version
     * @param newDate   the new version
     * @return          ArrayList < Descriptor > the descriptors introduced between the two years
     */
    public ArrayList <Descriptor> getNewDescriptors(String oldDate, String newDate){
        String XMLfileOldSCR = inputFolder+"\\supp"+oldDate+".xml";
        String XMLfileOld = inputFolder+"\\desc"+oldDate+".xml";
        String XMLfileNew = inputFolder+"\\desc"+newDate+".xml";             
        
        // Get old descriptors
        ArrayList <Descriptor> descriptorsOld = loadDescriptors(XMLfileOld);
        // Get old concepts
        HashMap <String,Descriptor> conceptToDescriptorOld = getConceptUIToDescriptorMap(descriptorsOld);
        // Get old SCRs
        ArrayList <SCR> CSROld = loadSCRs(XMLfileOldSCR);

        // Calculate average No of concepts per SCR andper Descriptor     
        if(MeSHDiffHarvester.calculateConceptAVGs){
            System.out.println(" Avg No of concepts per SCR :" + getConceptAVGinSCR(CSROld));
            System.out.println(" Avg No of concepts per descriptor :" + getConceptAVGinD(descriptorsOld));
        }
        
        HashMap <String, SCR> conceptUI2SCROld = new HashMap <>();  // Concept UI to corresponding SCR
        HashMap <String, SCR> term2SCROld = new HashMap <>();       // Concept Term (normalized) to corresponding SCR
        HashMap <String, SCR> termRaw2SCROld = new HashMap <>();    // Concept Term (raw) to corresponding SCR
        HashMap <String, SCR> SCRsOld = new HashMap <>();           // SCR UI to corresponding SCR object
        // Update some maps for searching SCRs
        for(SCR tmpSCR : CSROld){
            conceptUI2SCROld.put(tmpSCR.getPreferredConceptUI(), tmpSCR);
            ArrayList<String> terms = tmpSCR.getTerms();
            for (String termRaw : terms){
                String term = normalizeTerm(termRaw);
                term2SCROld.put(term, tmpSCR);
                termRaw2SCROld.put(termRaw, tmpSCR);
                SCRsOld.put(tmpSCR.getSCRUI(), tmpSCR);
            }
        }
        
        System.out.println(oldDate + " desciptors "+descriptorsOld.size() );
        // Get new descriptors
        ArrayList <Descriptor> descriptorsNew;
        descriptorsNew = loadDescriptors(XMLfileNew);
        
        System.out.println(newDate + " desciptors "+descriptorsNew.size() );
        // Find difference
        ArrayList <Descriptor> descriptorsDiff = new ArrayList <Descriptor>();

        descriptorsNew.removeAll(descriptorsOld);
        System.out.println("New descriptors in "+ newDate +": " + descriptorsNew.size() + " ");  
        // Get descriptor details from now
        descriptorsDiff.addAll(this.descriptorsNow);
        descriptorsDiff.retainAll(descriptorsNew);
        System.out.println("New descriptors still existing in "+ this.dateNow +": " + descriptorsDiff.size() + " "); 
        
        // Update difference with previous concept host  
        for(Descriptor d : descriptorsDiff){
            String preferredConceptUI = d.getPreferredConceptUI();
            if(conceptToDescriptorOld.keySet().contains(preferredConceptUI)){
                // This is an old subordinate concept upgraded into Descriptor
                // Udate previous host position
                d.setPreviousConceptHost(conceptToDescriptorOld.get(preferredConceptUI));
                d.setPreviousConceptRelation(d.getPreviousConceptHost().getConceptRelations().get(d.getPreferredConceptUI()));
            } else {// Is this an old SCR concept upgraded into Descriptor?
                SCR oldSCR = extractSCR(d, conceptUI2SCROld, term2SCROld, termRaw2SCROld, SCRsOld);
                if(oldSCR != null){
                    // Yes, this an old SCR concept 
                    d.setPreviousSCR(oldSCR);                    
                } else { // No, no old SCR found.
                    // This is a totally novel concept                      
                }
            } 
//            System.out.println(d.getPreviousIndexingList() + " - " + d.getPreviousConceptHost() + " - " + d.getPreviousSCR());
        }
//        System.out.println("\t\t in : " + term2SCROld.keySet());                        

        return descriptorsDiff;
    }

    /**
     * Find the hierarchical relation(s) between a descriptor d and a previous host descriptor ph.
     * @param d     The new descriptor
     * @param phName    The previous host name
     * @return      HashSet < String > the hierarchical relation(s) between d and ph
     */
    public HashSet<String> getHierarchicalRelations(Descriptor d, String phName){
        HashSet<String> hrs = new HashSet<>();
        if(this.descriptorExists(phName)){
            Descriptor ph = this.descriptorNowTerms.get(phName);
            hrs = getHierarchicalRelations(d, ph);
        } else { // ph doesn't exist
            // Undefined
            hrs.add("und");
        }
        return hrs;
    }    
    
    /**
     * Find the hierarchical relation(s) between a descriptor d and a previous host descriptor ph.
     * @param d     The new descriptor
     * @param ph    The previous host
     * @return      HashSet < String > the hierarchical relation(s) between d and ph
     */
    public HashSet<String> getHierarchicalRelations(Descriptor d, Descriptor ph){
        HashSet<String> hrs = new HashSet<>();
        if(this.descriptorExists(ph)){
            Descriptor phNow = this.descriptorsNowMap.get(ph.getDescriptorUI());
            Descriptor dNow = this.descriptorsNowMap.get(d.getDescriptorUI());
            for(String phTreeNumber : phNow.getTreeNumbers()){
                for(String dTreeNumber : dNow.getTreeNumbers()){
                    if(dTreeNumber.contains(phTreeNumber)){
                        // ancestor
                        hrs.add("anc");
                    } else if(phTreeNumber.contains(dTreeNumber)){
                        // descendant
                        hrs.add("des");                   
                    } else if(phTreeNumber.equals(dTreeNumber)){
                        // The two descriptors have identical tree number, this is not compatib with the model 
                        // identical
                        hrs.add("ide");   
                        System.out.println("The descriptors have a common treenumber" + d + " > " + phNow);
                    } else {
                        // Unrelated
                        hrs.add("unr");                                      
                    }
                }        
            }
        } else { // ph doesn't exist
            // Undefined
            hrs.add("und");                                      
        }    
        if(hrs.isEmpty()){
                        System.out.println("Empty relation found" + d + " > " + ph);
        }
        return hrs;
    }
    
    /**
     * Find the set of parent Descriptors for a Descriptor
     * @param d     The new descriptor
     * @return      HashSet < Descriptor> the parents of a descriptor
     */
    public HashSet<Descriptor> getParents(Descriptor d){
        HashSet<Descriptor> parents = new  HashSet<>();
        for(String treeNumber : d.getTreeNumbers()){
            if(treeNumber.contains(".")){
                //Get parent
                int lastDot = treeNumber.lastIndexOf(".");                
                String parentTreeNumber = treeNumber.substring(0, lastDot);
                Descriptor parent = treeNumbers.get(parentTreeNumber);
                parents.add(parent);
            }        
        }
        if(parents.isEmpty()){
            System.out.println("\t Warning : The decriptor " + d +" has " + parents.size() + " parents");
        }
        return parents;
    }
 
    /**
     * Get all Descriptors that are descendants of the given descriptor.
     * @param d
     * @return 
     */
    public HashSet<Descriptor> getDescendants(Descriptor d){
        HashSet<Descriptor> descenants = new  HashSet<>();
        for(String dtn : d.getTreeNumbers()){
            //Get descenant descriptors
            for(String tn : treeNumbers.keySet()){
                if(tn.startsWith(dtn) && !tn.equals(dtn)){//This is a descenant of Descriptor with treeNumber                            
                    descenants.add(treeNumbers.get(tn));
                }
            }
        }
        return descenants;
    }
    
    /**
     * Check if Descriptor d exists in the current version of MeSH
     * @param d a descriptor
     * @return 
     */
    public boolean descriptorExists(Descriptor d){
        boolean response = false;
        if( d != null && d instanceof Descriptor && this.descriptorsNow.contains(d)){
            response = true;
        } 
        return response;
    }
    
    /**
     * Check if Descriptor d exists in the current version of MeSH
     * @param d the name of a descriptor
     * @return 
     */
    public boolean descriptorExists(String d){
        boolean response = false;
        if( d != null && d instanceof String && this.descriptorNowTerms.keySet().contains(d)){
            response = true;
        } 
        return response;
    }
    
    /**
     * Get a descriptor for the current version of MeSH given any the UID
     * @param id
     * @return 
     */
    public Descriptor getDesciprotByID(String id){
        Descriptor response = null;
        if( id != null && id instanceof String && this.descriptorsNowMap.keySet().contains(id)){
            response = this.descriptorsNowMap.get(id);
        } 
        return response;
    }
    
    /**
     * Get a descriptor for the current version of MeSH given any term of it
     * @param term
     * @return 
     */
    public Descriptor getDesciprotByTerm(String term){
        Descriptor response = null;
        if( term != null && term instanceof String && this.descriptorNowTerms.keySet().contains(term)){
            response = this.descriptorNowTerms.get(term);
        } 
        return response;
    }
    
    /*  Paring MeSH XML files */
    /**
     * Harvests a MeSH SCR XML and returns a list of SCRs found in it
     * @param XMLFile
     * @return 
     */
    public static ArrayList <SCR> loadSCRs(String XMLFile){        
        // Connect to UMLS
//         UmlsHarvester umls = new UmlsHarvester("umls","root","testpass1!"); // UMLS full DataBase        
        System.out.println(" " + new Date().toString() + " Harvest MeSH SCR ");                                                                                      
        System.out.println(" \t\t" + XMLFile);                                                                                      
        Date start = new Date();
        JSONArray descriptorJSONList = LoadSCRXMLFile(XMLFile);
//        JSONArray PreferredConcepts = new JSONArray();
        ArrayList <SCR> descriptors = new ArrayList <>();
        
        for(Object o : descriptorJSONList){
//            System.out.println(o);
            descriptors.add(new SCR(o));            
        }
        return descriptors;
    }  
    
    /**
     * Harvests a MeSH XML and returns a list of Descriptors found in it
     * @param XMLFile
     * @return 
     */
    public static ArrayList <Descriptor> loadDescriptors(String XMLFile){        
        System.out.println(" " + new Date().toString() + " Harvest MeSH ");                                                                                      
        System.out.println(" \t\t" + XMLFile);                                                                                      
        Date start = new Date();
        JSONArray descriptorJSONList = LoadMeSHXMLFile(XMLFile);
        ArrayList <Descriptor> descriptors = new ArrayList <>();
        
        for(Object o : descriptorJSONList){
            descriptors.add(new Descriptor(o));            
        }
        return descriptors;
    }   
 
    /** Event driven parsing of MeSH SCR XML data into a processor
     *      parses XMLfile and calls handlers for XML elements found
     * @param processor
     * @param fileName 
     */
    public static void loadSCR(JsonArrayProcessor processor, String fileName) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser parser = factory.newSAXParser();
            File file = new File(fileName);
            SCRHandler mlcHandler = new SCRHandler(processor);

            parser.parse(file, mlcHandler);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            System.out.println(" " + new Date().toString() + " load(...) method caught a " + e.getClass() + " with message: " + e.getMessage());
        }
    }    
    
    /** Event driven parsing of MeSH XML data into a processor
     *      parses XMLfile and calls handlers for XML elements found
     * @param processor
     * @param fileName 
     */
    public static void loadMeSH(JsonArrayProcessor processor, String fileName) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser parser = factory.newSAXParser();
            File file = new File(fileName);
            MeSHHandler mlcHandler = new MeSHHandler(processor);

            parser.parse(file, mlcHandler);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            System.out.println(" " + new Date().toString() + " load(...) method caught a " + e.getClass() + " with message: " + e.getMessage());
        }
    }
    
    /**
     *  Reads an XML file of MeSH entries and parses them (the event driven way) into a JSONArray  
     * @param XMLfile
     * @return 
     */
    public static JSONArray LoadMeSHXMLFile(String XMLfile){
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        loadMeSH(processor,XMLfile);
        return articleList;
    }     

    /**
     *  Reads an XML file of MeSH SCR entries and parses them (the event driven way) into a JSONArray  
     * @param XMLfile    The XML file with entries to parse
     * @return           a JSONArray with all entries read from the XML files as JSON objects
     */    
    public static JSONArray LoadSCRXMLFile(String XMLfile){
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        loadSCR(processor,XMLfile);
        return articleList;
    }   
    
    /*  Naming, Writing and Reading files and folders   */
    
    /**
     * Get the path to a folder for saving data for the specific comparison dates.
     * @param oldDate
     * @param newDate
     * @return 
     */    
    public String getCurrentExperimentFolder(String oldDate, String newDate){
//        return this.experimentFolder+"\\"+oldDate + "_" + newDate;
        return this.experimentFolder; //We don't actualy need an experiment folder in this version
    }
    
    /**
     * Get the path to a folder for saving data for the specific Descriptor.
     * @param curentExperimentFolder
     * @param d
     * @return 
     */    
    public String getCurrentDescriptorFolder(String curentExperimentFolder, Descriptor d){
        return curentExperimentFolder + "\\" + d.getDescriptorUI();        
    }
    
    /**
     * Get the name for the SumUp CSV file for the specific comparison dates.
     * @param oldDate
     * @param newDate
     * @return 
     */
    public String getSumUpCSVFile(String oldDate, String newDate){
        return (this.getCurrentExperimentFolder(oldDate, newDate) + "\\diff"+oldDate+"_"+newDate+".csv");
    }
    
    /**
     * Get the name for the SumUp CSV file for the specific comparison dates.
     * @param oldDate
     * @param newDate
     * @return 
     */
    public String getIntialCSVFile(String oldDate, String newDate){
        return (this.getCurrentExperimentFolder(oldDate, newDate) + "\\newDescriptors_"+newDate+".csv");
    }
    
    /**
     * Create a new CSVWriter for the given path to write data in a CSV.
     * @param path  a path to write data in a CSV
     * @return      a new CSVWriter
     */
    public static CSVWriter newCSVWriter(String path){
        CSVWriter w = null;
        try {
            w = new CSVWriter(new BufferedWriter(new OutputStreamWriter( new FileOutputStream(path), "UTF-8")),
//                    CSVWriter.DEFAULT_SEPARATOR,
                    ';',
                    CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        }
        return w;
    }
    
    /**
     * Load a list of descriptors from a SumUp CSV file
     * @param oldDate
     * @param newDate
     * @return 
     */
    public ArrayList <Descriptor> loadDescriptorsFromCSV(String oldDate, String newDate){
        String filePath = getSumUpCSVFile(oldDate, newDate);
        ArrayList <Descriptor> Descriptors = new ArrayList <Descriptor>();
        CSVParser parser = new CSVParserBuilder().withSeparator(';').build();
        try {                
            CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(new FileInputStream(filePath),"UTF8")).withCSVParser(parser).build();
            // Reading Records One by One in a String array
            String[] nextRecord;
//                PMID;Abstract;Title
            while ((nextRecord = csvReader.readNext()) != null) {
//                    System.out.println("PMID : " + nextRecord[0]);
                if(!nextRecord[0].equals("Descriptor UI")){
                    Descriptors.add(this.descriptorsNowMap.get(nextRecord[0]));
                }
            }
        System.out.println("\t"+Descriptors.size()+" descriptors loaded from "+filePath);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CsvValidationException ex) {
            Logger.getLogger(MeSHDiffHarvester.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Descriptors;
    }        
    
    /**
     * Convert a term to lower case and removed non alphanumeric characters to account for "safe" non-exact match of terms
     * @param termToNormalize
     * @return 
     */
    public static String normalizeTerm(String termToNormalize){
        return termToNormalize.replaceAll(re, "").trim().toLowerCase();
    }

    /** 
     * Get average number of concepts per SCR in a set of SCRs
     * @param SCRs      A set of SCRs
     * @return          The average number of concepts per SCR
     */
    private double getConceptAVGinSCR(ArrayList<SCR> SCRs) {
        double concepts = 0;
        double scrs = 0;
        for(SCR s : SCRs){
            scrs++;
            concepts += s.getConceptUIs().size();
        }
        System.out.println(" " + concepts + " concepts / " + scrs + " SCRs");
        if(scrs == 0){
            return 0;
        } else {
            return concepts/scrs;
        }
    }
    
    /**
     * Get average number of concepts per descriptor in a set of Descriptors
     * @param Desriptors    A set of Descriptors
     * @return              The average number of concepts per descriptor
     */
    private double getConceptAVGinD(ArrayList<Descriptor> Desriptors) {
        double concepts = 0;
        double scrs = 0;
        for(Descriptor s : Desriptors){
            scrs++;
            concepts += s.getConceptUIs().size();
        }
        System.out.println(" " + concepts + " concepts / " + scrs + " Descriptors");
        if(scrs == 0){
            return 0;
        } else {
            return concepts/scrs;
        }
    }
}
