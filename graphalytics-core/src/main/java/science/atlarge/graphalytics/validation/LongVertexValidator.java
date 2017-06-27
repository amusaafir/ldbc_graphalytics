/*
 * Copyright 2015 - 2017 Atlarge Research Team,
 * operating at Technische Universiteit Delft
 * and Vrije Universiteit Amsterdam, the Netherlands.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package science.atlarge.graphalytics.validation;

import it.unimi.dsi.fastutil.longs.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import science.atlarge.graphalytics.util.MemoryUtil;
import science.atlarge.graphalytics.validation.rule.ValidationRule;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class takes the output file generated by a platform for specific algorithm and the reference output for this algorithm.
 * Both files should be in graphalytics vertex file format. The values of the vertices are validate using the given validation
 * rule.
 *
 * @author Stijn Heldens
 * @author Tim Hegeman
 * @author Wing Lung Ngai
 */
public class LongVertexValidator extends VertexValidator {
	private static final Logger LOG = LogManager.getLogger(LongVertexValidator.class);
	private static final long MAX_PRINT_ERROR_COUNT = 100;

	final private Path outputPath;
	final private Path validationFile;
	final private ValidationRule<Long> rule;
	final private boolean verbose;

	public LongVertexValidator(Path outputPath, Path validationFile, ValidationRule<Long> rule, boolean verbose) {
		this.outputPath = outputPath;
		this.validationFile = validationFile;
		this.rule = rule;
		this.verbose = verbose;
	}

	public boolean validate() throws ValidatorException {
		Long2LongMap validationResults, outputResults;

		LOG.info("Validating contents of '" + outputPath + "'...");
		LOG.info(MemoryUtil.getMemoryStats());

		try {
			validationResults = parseFileOrDirectory(validationFile);
		} catch (IOException e) {
			throw new ValidatorException("Failed to read validation file '" + validationFile + "'");
		}

		try {
			outputResults = parseFileOrDirectory(outputPath);
		} catch (IOException e) {
			throw new ValidatorException("Failed to read output file/directory '" + outputPath + "'");
		}

		LongSet keys = new LongOpenHashSet(validationResults.keySet().size() + outputResults.keySet().size());
		keys.addAll(validationResults.keySet());
		keys.addAll(outputResults.keySet());

		long errorsCount = 0;

		long missingVertices = 0;
		long unknownVertices = 0;
		long incorrectVertices = 0;
		long correctVertices = 0;

		for (Long id: keys) {

			String error = null;
			Long outputValue = outputResults.get(id);
			Long correctValue = validationResults.get(id);

			if (outputValue == null) {
				missingVertices++;
				error = "Vertex " + id + " is missing";
			} else if (correctValue == null) {
				unknownVertices++;
				error = "Vertex " + id + " is not a valid vertex";
			} else if (!rule.match(outputValue, correctValue)) {
				incorrectVertices++;
				error = "Vertex " + id + " has value '" + outputValue + "', but valid value is '" + correctValue + "'";
			} else {
				correctVertices++;
			}

			if (error != null) {
				if (verbose && errorsCount < MAX_PRINT_ERROR_COUNT) {
					LOG.info(" - " + error);
				}

				errorsCount++;
			}
		}

		if (errorsCount >= MAX_PRINT_ERROR_COUNT) {
			LOG.info(" - [" + (errorsCount - MAX_PRINT_ERROR_COUNT) + " errors have been omitted] ");
		}

		if (errorsCount > 0) {
			LOG.info("Validation failed.");

			long totalVertices = correctVertices + incorrectVertices + missingVertices;

			LOG.info(String.format(" - Correct vertices: %d (%.2f%%)",
					correctVertices, (100.0 * correctVertices) / totalVertices));
			LOG.info(String.format(" - Incorrect vertices: %d (%.2f%%)",
					incorrectVertices, (100.0 * incorrectVertices) / totalVertices));
			LOG.info(String.format(" - Missing vertices: %d (%.2f%%)",
					missingVertices, (100.0 * missingVertices) / totalVertices));
			LOG.info(String.format(" - Unknown vertices: %d (%.2f%%)",
					unknownVertices, (100.0 * unknownVertices) / totalVertices));
		} else {
			LOG.info("Validation is successful.");
		}

		LOG.info(MemoryUtil.getMemoryStats());

		return errorsCount == 0;
	}

	private Long2LongMap parseFileOrDirectory(Path filePath) throws IOException {

		LOG.info(String.format("Parsing file/directory %s.", filePath));

		final Long2LongMap results = new Long2LongOpenHashMap();
		final AtomicLong counter = new AtomicLong(0);

		Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

				try(BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();

						if (line.isEmpty()) {
							continue;
						}

						String[] parts = line.split("\\s+", 2);
						try {
							Long vertexId = Long.parseLong(parts[0]);
							Long vertexValue = rule.parse(parts.length > 1 ? parts[1] : "");
							results.put(vertexId,  vertexValue);
							if(counter.incrementAndGet() % 100000000 == 0) {
								LOG.debug(String.format("Parsed %s lines from %s.", counter.get(), file.getFileName().toString()));
								LOG.debug(MemoryUtil.getMemoryStats());
							}
						} catch(Throwable e) {
							LOG.error("Skipped invalid line '" + line + "' of file '" + file.getFileName().toString() + "'");
						}
					}
					LOG.info(String.format("Parsed %s lines from %s.", results.size(), file.getFileName().toString()));
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return results;
	}
}
