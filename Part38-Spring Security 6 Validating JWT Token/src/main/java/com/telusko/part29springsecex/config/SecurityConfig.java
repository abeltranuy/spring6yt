package com.telusko.part29springsecex.config;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.telusko.part29springsecex.service.MyUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole('ADMIN')") en controladores y servicios
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;
    
    @Autowired
    private TokenBlackListFilter tokenBlacklistFilter;

    @Autowired
    private JsonAuthErrorHandler jsonAuthErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http.csrf(customizer -> customizer.disable())
                // Usa el bean corsConfigurationSource(); sin esta linea el CorsFilter no entra en la cadena
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(request -> request
                        // /error publico: si no, cualquier 404/500 se convierte en un 401 vacio
                        .requestMatchers("/login", "/registrar", "/logout", "/error").permitAll()
                        // Frontend estatico (SB Admin 2): solo html y assets, no package.json ni gulpfile
                        .requestMatchers("/*.html", "/css/**", "/js/**", "/img/**", "/vendor/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                // Sin httpBasic: la unica credencial aceptada en rutas protegidas es el JWT
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // El LogoutFilter por defecto intercepta POST /logout antes de llegar a UserController
                .logout(logout -> logout.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tokenBlacklistFilter, JwtFilter.class)
                .build();
				//      .logout(logout -> logout
				//      .logoutUrl("/api/auth/logout")
				//      .invalidateHttpSession(true) // Invalidate the session
				//      .clearAuthentication(true) // Clear authentication context
				//      .deleteCookies("JSESSIONID") // Delete JSESSIONID cookie
				//      .permitAll() // Allow access to the logout endpoint without authentication
				//)
				
				// Enable sessions;
				//.sessionManagement(s -> s
				//      .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				//);
        		//
        		//.authenticationManager(authenticationManager())
		     	// Configurar los endpoints publicos
		        //http.requestMatchers(HttpMethod.GET, "/auth/get").permitAll();
			
		        // Cofnigurar los endpoints privados
		        //http.requestMatchers(HttpMethod.POST, "/auth/post").hasAnyRole("ADMIN", "DEVELOPER");
		        //http.requestMatchers(HttpMethod.PATCH, "/auth/patch").hasAnyAuthority("REFACTOR");
		
		        // Configurar el resto de endpoint - NO ESPECIFICADOS
		        //http.anyRequest().denyAll();
        
        		//.requestMatchers("/api/admin/**").hasRole("ADMIN")
        		//.requestMatchers("/api/user/**").hasRole("USER")
    }
    
    @Bean
    public AuthenticationManager authenticationManager(MyUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }
    
    @Bean
    public static PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200")); // Angular
        configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS"));
        // Authorization permitido en la peticion (enviar el JWT) y expuesto en la respuesta (leerlo tras /login)
        configuration.setAllowedHeaders(List.of(AUTHORIZATION, CONTENT_TYPE));
        configuration.setExposedHeaders(List.of(AUTHORIZATION));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /*
    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(myUserDetailsService());
        authenticationProvider.setPasswordEncoder(passwordEncoder());

        return new ProviderManager(authenticationProvider);
    }
    @Bean
    public static MyUserDetailsService myUserDetailsService(){
        return new MyUserDetailsService();
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    */
}
