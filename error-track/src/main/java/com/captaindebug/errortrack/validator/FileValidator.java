/**
 * Copyright 2014 Marin Solutions
 */
package com.captaindebug.errortrack.validator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.captaindebug.errortrack.Formatter;
import com.captaindebug.errortrack.Publisher;
import com.captaindebug.errortrack.Results;
import com.captaindebug.errortrack.Validator;
import com.google.common.annotations.VisibleForTesting;

/**
 * 
 * @author Roger
 * 
 */
@Service
public class FileValidator implements Validator {

	private static final Logger logger = LoggerFactory.getLogger(FileValidator.class);

	@Value("${following.lines}")
	private Integer extraLineCount;

	@Autowired
	@Qualifier("scan-for")
	private RegexValidator scanForValidator;

	@Autowired(required = false)
	@Qualifier("exclude")
	private RegexValidator excludeValidator;

	@Autowired
	@Qualifier("textFormatter")
	private Formatter formatter;

	@Autowired
	@Qualifier("emailPublisher")
	private Publisher publisher;

	@Autowired
	private FileAgeValidator fileAgeValidator;

	@Autowired
	private Results report;

	/**
	 * @see com.captaindebug.errortrack.file.FileFoundHandler#foundFile(java.io.File)
	 */
	@Override
	public <T> boolean validate(T obj) {

		boolean retVal = false;
		File file = (File) obj;
		if (fileAgeValidator.validate(file)) {
			report.addFile(file.getPath());
			checkFile(file);
			retVal = true;

			report.generate(formatter, publisher);
		}
		return retVal;
	}

	private void checkFile(File file) {

		try {
			BufferedReader in = createBufferedReader(file);
			readLines(in, file);
			in.close();
		} catch (Exception e) {
			logger.error("Error whilst processing file: " + file.getPath() + " Message: " + e.getMessage(), e);
		}
	}

	@VisibleForTesting
	BufferedReader createBufferedReader(File file) throws FileNotFoundException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		return in;
	}

	private void readLines(BufferedReader in, File file) throws IOException {
		int lineNumber = 0;
		String line;
		do {
			line = in.readLine();
			if (isNotNull(line)) {
				processLine(line, file.getPath(), ++lineNumber, in);
			}
		} while (isNotNull(line));
	}

	private boolean isNotNull(Object obj) {
		return obj != null;
	}

	private int processLine(String line, String filePath, int lineNumber, BufferedReader in) throws IOException {

		if (canValidateLine(line) && scanForValidator.validate(line)) {
			List<String> lines = new ArrayList<String>();
			lines.add(line);
			addExtraDetailLines(in, lines);
			report.addResult(filePath, lineNumber, lines);
			lineNumber += extraLineCount;
		}

		return lineNumber;
	}

	private boolean canValidateLine(String line) {
		boolean retVal = true;
		if (isNotNull(excludeValidator)) {
			retVal = !excludeValidator.validate(line);
		}
		return retVal;
	}

	private void addExtraDetailLines(BufferedReader in, List<String> lines) throws IOException {

		for (int i = 0; i < extraLineCount; i++) {
			String line = in.readLine();
			if (isNotNull(line)) {
				lines.add(line);
			} else {
				break;
			}
		}
	}

}
