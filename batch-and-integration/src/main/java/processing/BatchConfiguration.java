package processing;

import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.CommandLineJobRunner;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

	public static void main(String[] args) {
		SpringApplication.run(BatchConfiguration.class, args);
	}

	// NB: this, or something like this to launch the job, is only needed if you specify
	// NB: .. spring.batch.job.enabled=false (not the default)
	@Bean
	CommandLineRunner runner(JobLauncher launcher,
	                         Job job ,
	                         @Value("${file:data.csv}") Resource resource) {
		return args -> {
			JobParameters jobParameters = new JobParametersBuilder()
					.addString("file", resource.getFile().getAbsolutePath())
					.toJobParameters();
			JobExecution jobExecution = launcher.run(job, jobParameters);
			System.out.println(jobExecution.getExitStatus());
		};
	}

	@Bean
	DefaultLineMapper<Contact> lineMapper() {

		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames("fullName,email".split(","));

		BeanWrapperFieldSetMapper<Contact> mapper = new BeanWrapperFieldSetMapper<>();
		mapper.setTargetType(Contact.class);

		DefaultLineMapper<Contact> defaultLineMapper = new DefaultLineMapper<>();
		defaultLineMapper.setFieldSetMapper(mapper);
		defaultLineMapper.setLineTokenizer(tokenizer);
		return defaultLineMapper;
	}

	@Bean
	@StepScope
	ItemReader<Contact> fileReader(@Value("#{stepExecutionContext['file']}") Resource pathToFile,
	                               DefaultLineMapper<Contact> lm) {
		FlatFileItemReader<Contact> itemReader = new FlatFileItemReader<>();
		itemReader.setResource(pathToFile);
		itemReader.setLineMapper(lm);
		return itemReader;
	}

	@Bean
	ItemWriter<Contact> jdbcWriter(DataSource dataSource) {
		JdbcBatchItemWriter<Contact> batchItemWriter = new JdbcBatchItemWriter<>();
		batchItemWriter.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
		batchItemWriter.setDataSource(dataSource);
		batchItemWriter.setSql("insert into contact ( full_name, email) values ( :fullName, :email )");
		return batchItemWriter;
	}

	@Bean
	JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

 	@Bean
    Job job(JobBuilderFactory factory, Step step) {
		return factory.get("etl")
				.start(step)
				.build();
	}

	@Bean
	Step step(StepBuilderFactory factory, ItemReader<Contact> fileReader,
	          ItemWriter<Contact> jdbcWriter) {
		return factory
				.get("file-to-jdbc-step")
				.<Contact, Contact>chunk(5)
				.reader(fileReader)
				.writer(jdbcWriter)
				.build();
	}

}
