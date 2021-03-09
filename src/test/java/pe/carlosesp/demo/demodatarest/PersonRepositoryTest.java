package pe.carlosesp.demo.demodatarest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.carlosesp.demo.demodatarest.domain.Person;
import pe.carlosesp.demo.demodatarest.repository.PersonRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class PersonRepositoryTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    private List<Person> personList;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        personList = asList(
                personRepository.save(new Person("Martin", "Fowler")),
                personRepository.save(new Person("Kent", "Beck")),
                personRepository.save(new Person("Vaughn", "Vernon"))
        );
    }

    @Test
    public void getAllPeople() throws Exception {
        this.mockMvc.perform(get("/people"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("_embedded").exists())
                .andExpect(jsonPath("_embedded.people", hasSize(3)))
                .andExpect(jsonPath("_embedded.people[*].firstName",
                        hasItems(personList.stream().map(Person::getFirstName).toArray())))
                .andExpect(jsonPath("_embedded.people[*].lastName",
                        hasItems(personList.stream().map(Person::getLastName).toArray())))
                .andExpect(jsonPath("_links").exists())
                .andExpect(jsonPath("_links.self.href", endsWith("/people")))
                .andExpect(jsonPath("page").exists())
                .andExpect(jsonPath("page.totalElements").value(3));
    }

    @Test
    public void getPersonById() throws Exception {
        Person expectedPerson = personList.get(0);
        this.mockMvc.perform(get("/people/{id}", expectedPerson.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("firstName").value(expectedPerson.getFirstName()))
                .andExpect(jsonPath("lastName").value(expectedPerson.getLastName()))
                .andExpect(jsonPath("_links").exists())
                .andExpect(jsonPath("_links..href",
                        hasItems(endsWith("/people/" + expectedPerson.getId()))));
    }
    @Test
    public void getPersonThroughSelfLink() {
        Person expectedPerson = personList.get(0);
        String baseUrl = "http://localhost:" + this.port + "/people/" + expectedPerson.getId();

        Traverson traverson = new Traverson(URI.create(baseUrl), MediaTypes.HAL_JSON);
        Traverson.TraversalBuilder client = traverson.follow(IanaLinkRelations.SELF.value());
        Person personModel = client.toObject(Person.class);

        assertThat(personModel).isNotNull();
        assertThat(expectedPerson.getFirstName()).isEqualTo(personModel.getFirstName());
        assertThat(expectedPerson.getLastName()).isEqualTo(personModel.getLastName());
    }

    @Test
    public void createPerson() throws Exception {
        Person newPerson = new Person("John", "Doe");
        String body = this.objectMapper.writeValueAsString(newPerson);

        MvcResult mvcResult = this.mockMvc.perform(post("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("/people/")))
                .andExpect(jsonPath("$").doesNotExist())
                .andExpect(redirectedUrlPattern("http://*/people/*"))
                .andReturn();

        String location = mvcResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String personId = location.substring(location.lastIndexOf("/") + 1);
        assertThat(personId).isNotEmpty();

        Optional<Person> personCreated = personRepository.findById(Long.valueOf(personId));
        assertThat(personCreated.isPresent()).isTrue();
        assertThat(personCreated.get().getFirstName()).isEqualTo(newPerson.getFirstName());
        assertThat(personCreated.get().getLastName()).isEqualTo(newPerson.getLastName());

        this.mockMvc.perform(get("/people/{id}", personId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("firstName").value(newPerson.getFirstName()))
                .andExpect(jsonPath("lastName").value(newPerson.getLastName()))
                .andExpect(jsonPath("_links.self.href").value(location));
    }

    @Test
    public void findCustomQueries() throws Exception {
        this.mockMvc.perform(get("/people/search"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("_links").exists())
                .andExpect(jsonPath("_links.findByLastName").exists())
                .andExpect(jsonPath("_links.findByLastName.href",
                        endsWith("/people/search/findByLastName{?name}")))
                .andExpect(jsonPath("_links.findByLastName.templated").value(true));

        Person expectedPerson = personList.get(0);
        this.mockMvc.perform(get("/people/search/findByLastName")
                .param("name", expectedPerson.getLastName()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("_embedded").exists())
                .andExpect(jsonPath("_embedded.people", hasSize(1)))
                .andExpect(jsonPath("_embedded.people[0].firstName").value(expectedPerson.getFirstName()))
                .andExpect(jsonPath("_embedded.people[0].lastName").value(expectedPerson.getLastName()))
                .andExpect(jsonPath("_embedded.people[0]._links").exists())
                .andExpect(jsonPath("_embedded.people[0]._links.self.href",
                        endsWith("/people/" + expectedPerson.getId())));
    }

    @Test
    public void replacePerson() throws Exception {
        Person originalPerson = personList.get(0);
        Person newPerson = new Person("John", "Smith");
        String body = this.objectMapper.writeValueAsString(newPerson);
        System.out.println(body);
        MvcResult mvcResult = this.mockMvc.perform(put("/people/{id}", originalPerson.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", endsWith("/people/" + originalPerson.getId())))
                .andExpect(jsonPath("$").doesNotExist())
                .andExpect(redirectedUrlPattern("http://*/people/" + originalPerson.getId()))
                .andReturn();

        String location = mvcResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String personId = location.substring(location.lastIndexOf("/") + 1);
        assertThat(personId).isNotNull();
        assertThat(Long.valueOf(personId)).isEqualTo(originalPerson.getId());

        this.mockMvc.perform(get("/people/{id}", personId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("firstName").value(newPerson.getFirstName()))
                .andExpect(jsonPath("lastName").value(newPerson.getLastName()))
                .andExpect(jsonPath("_links.self.href").value(location));
    }

    @Test
    public void updatePerson() throws Exception {
        Person originalPerson = personList.get(0);
        String updatedFirstName = "Martin Jr.";
        this.mockMvc.perform(patch("/people/{id}", originalPerson.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"" + updatedFirstName + "\"}")
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$").doesNotExist());

        this.mockMvc.perform(get("/people/{id}", originalPerson.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("firstName").value(updatedFirstName))
                .andExpect(jsonPath("lastName").value(originalPerson.getLastName()))
                .andExpect(jsonPath("_links.self.href",
                        containsString("/people/" + originalPerson.getId())));
    }

    @Test
    public void deletePerson() throws Exception {
        this.mockMvc.perform(get("/people"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("_embedded").exists())
                .andExpect(jsonPath("_embedded.people", hasSize(3)))
                .andExpect(jsonPath("_embedded.people[*].firstName",
                        hasItems(personList.stream().map(Person::getFirstName).toArray())))
                .andExpect(jsonPath("_embedded.people[*].lastName",
                        hasItems(personList.stream().map(Person::getLastName).toArray())))
                .andExpect(jsonPath("page").exists())
                .andExpect(jsonPath("page.totalElements").value(3));

        Person deletedPerson = personList.get(0);
        this.mockMvc.perform(delete("/people/{id}", deletedPerson.getId()))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$").doesNotExist());

        this.mockMvc.perform(get("/people/{id}", deletedPerson.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").doesNotExist());
    }

}
