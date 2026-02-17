package com.mayo.hospitalintegration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * GraphQL Controller for hospital integration service.
 * The actual GraphQL endpoint is handled by GraphQL Java Kickstart starter.
 * This controller provides access to the GraphiQL IDE.
 */
@Controller
public class GraphQLController {

    /**
     * Redirect to GraphiQL IDE for testing GraphQL queries.
     * The GraphQL endpoint is available at /graphql
     * The GraphiQL IDE is available at /graphiql
     */
    @GetMapping("/graphql-ui")
    public String graphiql() {
        return "redirect:/graphiql";
    }
}