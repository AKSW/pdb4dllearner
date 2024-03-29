package de.unileipzig.bioinf.pdb2dllearner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.HTMLLayout;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.LearningProblemUnsupportedException;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;

public class HelixRDFCreator {
	
	private static Logger _logger = Logger.getLogger(HelixRDFCreator.class);
	private static Logger _rootLogger = Logger.getRootLogger();
	
	private static String _dataDir = "data/";
	private static File _dir = new File(_dataDir);
	
	/**
	 * @param args
	 * TODO: remove beginsAt, endsAt from model
	 */
	public static void main(String[] args) {

		// create loggers (a simple logger which outputs
		// its messages to the console and a log file)
		
		// logger 1 is the console, where we print only info messages;
		// the logger is plain, i.e. does not output log level etc.
		Layout layout = new PatternLayout();

		ConsoleAppender consoleAppender = new ConsoleAppender(layout);
		// setting a threshold suppresses log messages below this level;
		// this means that if you want to e.g. see all trace messages on
		// console, you have to set the threshold and log level to trace
		// (but we recommend just setting the log level to trace and observe
		// the log file)
		consoleAppender.setThreshold(Level.INFO);
		
		// logger 2 is writes to a file; it records all debug messages
		// (you can choose HTML or TXT)
		boolean htmlLog = false;
		Layout layout2 = null;
		FileAppender fileAppenderNormal = null;
		String fileName;
		if(htmlLog) {
			layout2 = new HTMLLayout();
			fileName = _dataDir + "log/log.html";
		} else {
			// simple variant: layout2 = new SimpleLayout();
			layout2 = new PatternLayout("%d [%t] %-5p %c : %m%n");
			fileName = _dataDir + "log/log.txt";
		}
		try {
			fileAppenderNormal = new FileAppender(layout2, fileName, false);
			fileAppenderNormal.setThreshold(Level.INFO);
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		// add both loggers
		_rootLogger.removeAllAppenders();
		_rootLogger.addAppender(consoleAppender);
		_rootLogger.addAppender(fileAppenderNormal);
		_rootLogger.setLevel(Level.INFO);
		
		
		Boolean fasta = true;
		
		/*
		 * rdfConf = true -> write out the .rdf and .conf-Files 
		 * rdfConf = false -> does not generate those files
		 */
		Boolean rdfConf = true;
		
		/*
		 * arff = true -> write out .arff-Files
		 * arff = false -> does not generate those files
		 */
		Boolean arff = true;
		/*
		 * load = true -> load alle .rdf, .conf and .arff Files that can be found within the directory dataDir
		 * load = false -> don't load anything
		 */
		Boolean dlLearn = false;
		Boolean wekaLearn = false;
		
		int dataSet = 4;

		/*
		 * data for test purpose
		 */

		/* +++ Problem IDs +++
		 * Warum funktionieren die Abfragen mit den untenstehenden PDB IDs nicht???
		 */
//		PDBProtein testProtein = new PDBProtein("1HTR","P");
		PDBProtein testProtein = new PDBProtein("2W9Y","A");
//		PDBProtein testProtein = new PDBProtein("3A4R","A");

		
		/*
		 * create a training data set
		 */
		ProteinDataSet proteinSet;
		
		switch (dataSet) {
            case 1:	
            	proteinSet = ProteinDataSet.bt426();
            	break;
            case 2:
            	proteinSet = ProteinDataSet.plp273();
            	break;
            case 3:
            	proteinSet = ProteinDataSet.plp364();
            	break;
            case 4:
            	proteinSet = ProteinDataSet.plp399();
            	break;
            default:
            	proteinSet = new ProteinDataSet(testProtein); 
            	break;
		}

		/*
		 * generate a PdbRdfModel for every pdbID
		 */
		
		PDBIdRdfModel trainmodel;
		
		for (int i = 0; i < proteinSet.getProteinset().size(); i++)
		{
			if (rdfConf || arff) {
				
				PDBProtein protein = proteinSet.getProteinset().get(i);
				_logger.info("Start with extracting data from: " + protein.getPdbID());
				String pdbDir = _dataDir +  protein.getPdbID() + "/";
				_logger.info("Directory: " +pdbDir);
				File directory = new File(pdbDir);
				if(! directory.exists()) {
					_logger.info("Creating directory " + pdbDir);
					directory.mkdir();
				}
				//
				//String arffFilePath = pdbDir + protein.getArffFileName();
				
				_logger.info("PDB ID: " + protein.getPdbID());
				_logger.info("Chain ID: " + protein.getChainID());
				
				trainmodel = new PDBIdRdfModel(protein);
				
				if (fasta){
					trainmodel.createFastaFile(pdbDir);
				}
				
				
				/*
				 * if arff = true create pdbID.arff files
				 */
				
				/* 
				 * as we have sometimes to handle several amino acid chains we need the first
				 * amino acid of every chain, they are returned within a ResIterator
				 */
				
				if (arff)
				{
					ResIterator niter = trainmodel.getFirstAA();
					createNumericArffFile(pdbDir, trainmodel, niter);
					createNominalArffFile(pdbDir, trainmodel, niter);
				}
				
				/*
				 * remove all triples that contain information about begin and end of helices
				 */
				Property beginsAt = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "beginsAt");
				trainmodel.removeStatementsWithPoperty(beginsAt);
				Property endsAt = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "endsAt");
				trainmodel.removeStatementsWithPoperty(endsAt);
				Resource residue = ResourceFactory.createResource("http://bio2rdf.org/pdb:Residue");
				trainmodel.removeStatementsWithObject(residue);
				Property isPartOf = ResourceFactory.createProperty("http://purl.org/dc/terms/", "isPartOf");
				trainmodel.removeStatementsWithPoperty(isPartOf);
				Property hasValue = ResourceFactory.createProperty("http://bio2rdf.org/pdb:", "hasValue");
				trainmodel.removeStatementsWithPoperty(hasValue);
				/*
				 * we add the information which amino acid is the fourth predecessor of which other amino acid 
				 */
				trainmodel.addDistanceInfo();
				
				/*
				 * if rdfConf = true create pdbID.rdf and *.conf files
				 */
				
				
				
				if(rdfConf)
				{
					String rdfFilePath = pdbDir + protein.getRdfFileName();
					try
			    	{
						/*
						 * creatConfFile()
						 * writes the conf-Files and saves there File-objects in:
						 * confFileForAll and confFilePerResidue
						 */
						createConfFile(pdbDir, trainmodel);
						
						PrintStream out = new PrintStream (new File(rdfFilePath));
						
						// Output results
						trainmodel.getModel().write(out, "RDF/XML");
	
						// Important - free up resources used running the query
						out.close();
			    	}
			    	catch (FileNotFoundException e)
			    	{
			    		_logger.error("File " + rdfFilePath + " konnte nicht gefunden werden!");
			    		e.printStackTrace();
			    	}
				}
				/*
				 * For every protein source species create a file that contains a list of all 
				 * proteins that originate from that particular species. If it already exists
				 * we will append to it.
				 */
				
				if (protein.getSpecies() != ""){
					File speciesProteins = new File(_dataDir + protein.getSpecies() + ".pos");
					
					try {
						String line =  protein.getPdbID() + "." + protein.getChainID()  + "." + protein.getSpecies() + "\n";
						FileWriter out = new FileWriter(speciesProteins, true);
						_logger.debug("Write " + line + " to file " + speciesProteins.getPath());
						out.write(line);
						out.close();
					} catch (FileNotFoundException e) {
						_logger.error("Could not find file " + speciesProteins.getPath() + speciesProteins.getName());
						e.printStackTrace();
					} catch (IOException e) {
						_logger.error("Something went wrong while trying to write to " + speciesProteins.getPath() + speciesProteins.getName());
						e.printStackTrace();
					}
				}
			}
		}
		
		if(dlLearn)
		{
			startDlLearner();
		}
			
		if(wekaLearn)
		{
			startWekaLearner();
		}
	}

	private static void startDlLearner(){
		HashMap<String, File> pdbIDConfFile = loadConfFiles(_dir);
		for (String pdbID : pdbIDConfFile.keySet()){
			try {
				new PDBDLLearner(pdbIDConfFile.get(pdbID));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ComponentInitException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (LearningProblemUnsupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private static void startWekaLearner() {
		HashMap<String, File> pdbIDArffFile = loadArffFiles(_dir);
		for (String pdbID: pdbIDArffFile.keySet()){
			try {
				new PDBWekaLearner(pdbIDArffFile.get(pdbID));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	private static HashMap<String,File> loadConfFiles (File dir){
		HashMap<String,File> confFiles = new HashMap<String,File>();
		_logger.info("Starting to load files in " + dir );
		File[] pdbDir = dir.listFiles(new DirectoryFileFilter());
		for (File activeDirectory : pdbDir) {
			File[] confFilesInActiveDirectory = activeDirectory.listFiles(new ConfFileFilter());
			_logger.info("Looking for Files in " + activeDirectory.getPath() );
			for (File confFile : confFilesInActiveDirectory) {
				String confFileName = confFile.getName().substring(0, confFile.getName().indexOf(".conf")); 
				confFiles.put(confFileName, confFile);
				_logger.info("Found .conf File " + confFile.getPath() );
			}
		}
		return confFiles;
	}
	
	
	private static HashMap<String,File> loadArffFiles (File dir){
		HashMap<String,File> arffFiles = new HashMap<String,File>();
		_logger.info("Starting to load files in " + dir );
		
		File[] pdbDir = dir.listFiles(new DirectoryFileFilter());
		for (File activeDirectory : pdbDir) {
			File[] arffFilesInActDir = activeDirectory.listFiles(new ArffFileFilter());
			_logger.info("Looking for .arff Files in " + activeDirectory.getPath());
			for (File arffFile : arffFilesInActDir) {
				String arffFileName = arffFile.getName().substring(0, arffFile.getName().indexOf(".arff"));
				arffFiles.put(arffFileName, arffFile);
				_logger.info("Found .arff File " + arffFile.getPath());
			}
		}
		return arffFiles;
	}
	
	private static void createConfFile(String pdbDir, PDBIdRdfModel model){
		try
    	{
			PDBProtein protein = model.getProtein();

			// the .conf file that contains all positives and negatives
			String confFilePath = pdbDir + protein.getConfFileName();
			PrintStream confFile = new PrintStream (new File(confFilePath));

			
			// knowledge source definition
			String ks = new String ("// knowledge source definition\n" + 
					"ks.type = \"OWL File\"\n" +
					"ks.fileName = \"AA_properties.owl\"\n" +
					"\n" +
					"ks.type = \"OWL File\"\n" + 
					"ks.fileName = \"" + protein.getRdfFileName() + "\"\n");
			// learning problem
			StringBuffer lp = new StringBuffer ("// learning problem\n" +
					"lp.type = \"posNegStandard\"" +
					"\n" +
					"lp.positiveExamples = { ");			
			
			// separate files that contain only positives and negatives of a distinct type
			HashMap<Resource, File> confFilePerResidue = 
				AminoAcids.getAllConfFiles(pdbDir, protein.getConfFileName());
			HashMap<Resource, PrintStream> resprint = 
				AminoAcids.getAminoAcidPrintStreamMap(confFilePerResidue);
			HashMap<Resource, StringBuffer> resourceStringBuffer = 
				AminoAcids.getAminoAcidStringBufferMap(lp.toString());
			
			// add knowledge source definition to <PDB ID>.conf files
			confFile.println(ks);
			
			// add knowledge source definition to <PDB ID>_<Amino Acid>.conf files
			Iterator<Resource> resources = resprint.keySet().iterator();
			while (resources.hasNext()){
				resprint.get(resources.next()).println(ks);
			}

			
			ArrayList<Resource> positives = model.getPositives();

			Property type = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "type");

			// add positive examples to <PDB ID>.conf and corresponding <PDB ID>_<Amino Acid>.conf files
			for (int i = 0 ; i < positives.size() ; i++ ) {
				lp.append("\"" + positives.get(i).getURI() + "\", ");
				try{
					Statement spo = model.getModel().getProperty(positives.get(i), type);
					resourceStringBuffer.get(spo.getResource()).append("\"" + positives.get(i).getURI() + "\", ");
				} catch (NullPointerException e) {
					// What was the Object that probably caused the pain?
					_logger.error("Object probably not in our HashMap: " +
							model.getModel().getProperty(positives.get(i), type).getResource());
					e.printStackTrace();
				}
			}
			
			if (lp.toString().contains(","))
				lp.deleteCharAt(lp.lastIndexOf(","));
			lp.append("}\n" +
					"lp.negativeExamples = { ");
			
			resources = resourceStringBuffer.keySet().iterator();
			while (resources.hasNext()){
				Resource residue = resources.next();
				if (resourceStringBuffer.get(residue).toString().contains(",")) 
					resourceStringBuffer.get(residue).deleteCharAt(
							resourceStringBuffer.get(residue).lastIndexOf(","));
				resourceStringBuffer.get(residue).append("}\n" +
						"lp.negativeExamples = { ");			
			}
			
			
			// add every amino acid in negative list to <PDB ID>.conf and its corresponding <PDB ID>_<Amino Acid>.conf
			ArrayList<Resource> negatives = model.getNegatives();
			for (int i = 0 ; i < negatives.size() ; i++ ) {
				lp.append("\"" + negatives.get(i).getURI() + "\", ");
				try{
					Statement spo = model.getModel().getProperty(negatives.get(i), type);
					resourceStringBuffer.get(spo.getResource()).append("\"" + negatives.get(i).getURI() + "\", ");
				} catch (NullPointerException e) {
					// What was the Object that probably caused the pain?
					_logger.error("Object probably not in our HashMap: " +
							model.getModel().getProperty(negatives.get(i), type).getResource());
					e.printStackTrace();
				}
			}
			
			// add learning problem to <PDB ID>.conf file
			// and write learning problem to file
			if (lp.toString().contains(","))
				lp.deleteCharAt(lp.lastIndexOf(","));
			lp.append("}\n");
			confFile.println(lp);

			// add learning problem to <PDB ID>_<Amino Acid>.conf files
			// and write learning problem to file
			resources = resourceStringBuffer.keySet().iterator();
			while (resources.hasNext()){
				Resource residue = resources.next();
				if (resourceStringBuffer.get(residue).toString().contains(","))
					resourceStringBuffer.get(residue).deleteCharAt(
							resourceStringBuffer.get(residue).lastIndexOf(","));
				resourceStringBuffer.get(residue).append("}\n");
				resprint.get(residue).println(resourceStringBuffer.get(residue));
			}
			
			// Important - free up resources used running the query
			confFile.close();
			
			Iterator<Resource> newkeys = resprint.keySet().iterator(); 
			while ( newkeys.hasNext() ){
				resprint.get(newkeys.next()).close();
			}
    	}
    	catch (IOException e)
    	{
    		_logger.error("OutputStream konnte nicht geschlossen werden!");
    	}
	}
	
	private static void createNumericArffFile(String pdbDir, PDBIdRdfModel model, ResIterator firstAAs){

		try {
			PDBProtein protein = model.getProtein();
			String arffFilePath = pdbDir + protein.getArffFileName();
			arffFilePath = arffFilePath.replace(".arff", ".numeric.arff");
			PrintStream out = new PrintStream (arffFilePath);
			_logger.debug("Creating numeric ARFF file: " + arffFilePath);
	
			/*
			 * RELATION
			 */
			String relation = "@RELATION " + protein.getPdbID();
			out.println(relation);
			_logger.debug(relation);
			
			/*
			 * ATTRIBUTES
			 */
			// Integer declaring Position in chain
			StringBuffer attributes = new StringBuffer(
					// Hydrophobicity   hydrophilic = 0; Hydrophobic = 1; aromatic = 2; aliphatic = 3
					"@ATTRIBUTE hydrophobicity NUMERIC\n" +
					// Polarity unpolar = 0 polar = 1; positive = 2; negative = 3; 
					"@ATTRIBUTE polarity NUMERIC\n" + 
					// Size Tiny = 0; Small = 1; Large = 2;
					"@ATTRIBUTE size NUMERIC\n"); 

			for (int i = -8; i <= 8; i++) {
				attributes.append("@ATTRIBUTE aa_position_" + i + " NUMERIC\n"); // amino acid at position $i from current amino acid 
			}
			attributes.append("@ATTRIBUTE in_helix NUMERIC\n"); // Helix = 1 Other = 0
					
			_logger.debug(attributes);
			out.println(attributes);
			
			/*
			 * @DATA
			 */
			String data = "@DATA\n";
			_logger.debug(data);
			out.println(data);
			
			// HashMap containing information about the properties of every amino acid
			HashMap<String, String> resdata = AminoAcids.getAminoAcidNumericArffAttributeMap();
			HashMap<String, String> resnum = AminoAcids.getAminoAcidNumber();
			ArrayList<Resource> positives = model.getPositives();
			ArrayList<Resource> negatives = model.getNegatives();
			String sequence = protein.getSequence();
			HashMap<Integer, Resource> posRes = model.getPositionResource();
			
			
			for ( int i = 0; i < sequence.length(); i++) {
				StringBuffer dataLine = new StringBuffer("");
				String key = Character.toString( sequence.charAt(i) );

				// add amino acid description to dataLine
				if ( resdata.containsKey(key) ){
					dataLine.append( resdata.get(key) + "," );
				} else {
					// 
					dataLine.append( resdata.get("X") + "," );
				}
				
				// add information about neighbouring amino acids to dataLine
				for (int j = (i - 8); j <= (i + 8) ; j++){
					try {
						dataLine.append( resnum.get(
								Character.toString(protein.getSequence().charAt(j))) + "," );
					} catch (IndexOutOfBoundsException e) {
						dataLine.append( "?," );
					}
				}
				
				// add information about positive or negative to dataLine
				if (positives.contains( posRes.get( new Integer(i) ))){
					dataLine.append( "1" );
				} else if (negatives.contains( posRes.get( new Integer(i) ))){
					dataLine.append( "0" );
				} else {
					dataLine.append( "?" );
				}
				
				_logger.debug(dataLine);
				out.println(dataLine);
				
			}
			
		} catch (FileNotFoundException e){
			e.printStackTrace();
		}
	}
	
	private static void createNominalArffFile(String pdbDir, PDBIdRdfModel model, ResIterator firstAAs){

		try {
			PDBProtein protein = model.getProtein();
			String arffFilePath = pdbDir + protein.getArffFileName();
			arffFilePath = arffFilePath.replace(".arff", ".nominal.arff");
			PrintStream out = new PrintStream (arffFilePath);
			_logger.debug("Creating nominal ARFF file: " + arffFilePath);
	
			/*
			 * RELATION
			 */
			String relation = "@RELATION " + protein.getPdbID();
			out.println(relation);
			_logger.debug(relation);
			
			/*
			 * ATTRIBUTES
			 */
			// Integer declaring Position in chain
			StringBuffer attributes = new StringBuffer(
					// Hydrophobicity   hydrophilic = 0; hydrophobic = 1; aromatic = 2; aliphatic = 3
					"@ATTRIBUTE hydrophob {hydrophilic, hydrophobic, aromatic, aliphatic}\n" + //  Hydrophilic = 0; Hydrophobic = 1; Very_hydrophobic = 2
					// Polarity unpolar = 0; polar = 1; positive = 2; negative = 3;
					"@ATTRIBUTE charge {unpolar, polar, positive, negative}\n" + // Negative = -1; Neutral = 0; Positive = 1
					// Size tiny = 0; small = 1; large = 2;
					"@ATTRIBUTE size {tiny, small, large}\n");

			for (int i = -8; i <= 8; i++) {
				attributes.append("@ATTRIBUTE aa_position_" + i + " {A,C,D,E,F,G,H,I,K,L,M,N,P,Q,R,S,T,V,W,Y}\n"); // amino acid at position $i from current amino acid
			}
			attributes.append("@ATTRIBUTE in_helix {Helix, Non_helix}\n"); // Helix = 1 Other = 0
					
			_logger.debug(attributes);
			out.println(attributes);
			
			/*
			 * @DATA
			 */
			String data = "@DATA\n";
			_logger.debug(data);
			out.println(data);
			
			// HashMap containing information about the properties of every amino acid
			HashMap<String, String> resdata = AminoAcids.getAminoAcidNominalArffAttributeMap();
			ArrayList<Resource> positives = model.getPositives();
			ArrayList<Resource> negatives = model.getNegatives();
			String sequence = protein.getSequence();
			HashMap<Integer, Resource> posRes = model.getPositionResource();
			
			for ( int i = 0; i < sequence.length(); i++) {
				StringBuffer dataLine = new StringBuffer("");
				String key = Character.toString( sequence.charAt(i) );

				// add amino acid description to dataLine
				if ( resdata.containsKey(key) ){
					dataLine.append( resdata.get(key) + "," );
				} else {
					// 
					dataLine.append( resdata.get("X") + "," );
				}
				
				// add information about neighbouring amino acids to dataLine
				for (int j = (i - 8); j <= (i + 8) ; j++){
					try {
						dataLine.append( protein.getSequence().charAt(j) + "," );
					} catch (IndexOutOfBoundsException e) {
						dataLine.append( "?," );
					}
				}
				
				// add information about positive or negative to dataLine
				if (positives.contains( posRes.get( new Integer(i) ))){
					dataLine.append( "Helix" );
				} else if (negatives.contains( posRes.get( new Integer(i) ))){
					dataLine.append( "Non_helix" );
				} else {
					dataLine.append( "?" );
				}
				
				_logger.debug(dataLine);
				out.println(dataLine);
				
			}
			
		} catch (FileNotFoundException e){
			e.printStackTrace();
		}
	}

}
