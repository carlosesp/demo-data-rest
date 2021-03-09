# Spring Data REST

Demo Project using Spring Data REST, that creates and retrieves Person objects 
stored in a database.

Spring Data REST takes features of Spring HATEOAS and Spring Data JPA, 
and combines them to create RESTful endpoints.

All it needs is an Interface extending PagingAndSortingRepository, and 
@RepositoryResource if you want to change the default path for the 
RESTful endpoints.

### Test Cases

- Get all people
- Get Person by Id
- Get Person through self Link
- Create Person
- Find custom queries (findByLastName)
- Replace Person
- Update Person
- Delete Person