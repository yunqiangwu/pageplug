package main

import (
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/gorilla/mux"
	"github.com/markbates/goth"
	"github.com/markbates/goth/providers/google"
	"github.com/spf13/viper"
	"gitlab.com/mobtools/internal-tools-server/api"
	"gitlab.com/mobtools/internal-tools-server/api/middleware"
	"gitlab.com/mobtools/internal-tools-server/models"
	"gitlab.com/mobtools/internal-tools-server/storage"
	"gitlab.com/mobtools/internal-tools-server/url"
)

const baseURL = "/"
const baseAPIURL = "/api"
const apiVersion = "/v1"

func main() {

	// Read all configurations
	parseConfig()

	// Initialize the database
	initializeDatastore()

	// Run any migrations on the datastore
	runMigrations()

	router := intializeServer()

	host := viper.GetString("server.host")
	port := viper.GetString("server.port")
	log.Fatal(http.ListenAndServe(host+":"+port, router))
}

func intializeServer() *mux.Router {
	router := mux.NewRouter()
	authProvider := viper.GetString("auth.provider")
	if authProvider == "google" {
		goth.UseProviders(
			google.New(viper.GetString("auth.key"), viper.GetString("auth.secret"), "http://localhost:"+viper.GetString("server.port")+viper.GetString("auth.callbackUrl")),
		)
	}

	// Auth Endpoints
	router.HandleFunc(url.LoginURL, middleware.Chain(api.Login, middleware.Method("GET"), middleware.Logging()))
	router.HandleFunc(url.AuthURL, middleware.Chain(api.InitiateAuth, middleware.Method("GET"), middleware.Logging()))
	router.HandleFunc(url.AuthCallbackURL, middleware.Chain(api.AuthCallback, middleware.Method("GET"), middleware.Logging()))
	router.HandleFunc(url.LogoutURL, middleware.Chain(api.Logout, middleware.Method("GET"), middleware.Logging()))
	router.HandleFunc(url.ProfileURL, middleware.Chain(api.GetUserProfile, middleware.Method("GET"), middleware.Logging()))

	// Account CRUD Endpoints

	// Component CRUD Endpoints
	router.HandleFunc(baseAPIURL+apiVersion+url.ComponentURL, middleware.Chain(api.GetComponents, middleware.Method("GET"), middleware.Authenticated(), middleware.Logging()))
	router.HandleFunc(baseAPIURL+apiVersion+url.ComponentURL, middleware.Chain(api.CreateComponents, middleware.Method("POST"), middleware.Authenticated(), middleware.Logging()))
	router.HandleFunc(baseAPIURL+apiVersion+url.ComponentURL, middleware.Chain(api.UpdateComponent, middleware.Method("PUT"), middleware.Authenticated(), middleware.Logging()))

	// Page CRUD Endpoints

	// Query CRUD Endpoints
	router.HandleFunc(baseAPIURL+apiVersion+url.QueryURL+"/execute", middleware.Chain(api.PostQuery, middleware.Method("POST"), middleware.Authenticated(), middleware.Logging()))
	router.HandleFunc(baseAPIURL+apiVersion+url.QueryURL, middleware.Chain(api.CreateQuery, middleware.Method("POST"), middleware.Authenticated(), middleware.Logging()))
	router.HandleFunc(baseAPIURL+apiVersion+url.QueryURL, middleware.Chain(api.UpdateQuery, middleware.Method("PUT"), middleware.Authenticated(), middleware.Logging()))

	return router
}

func initializeDatastore() {
	var err error
	dialect := viper.GetString("datastore.dialect")
	storage.StorageEngine, err = storage.CreateDatastore(dialect)
	if err != nil {
		panic(fmt.Errorf("Exception while creating datastore"))
	}
}

func parseConfig() {
	viper.AddConfigPath(".")

	err := viper.ReadInConfig()
	if err != nil {
		panic(fmt.Errorf("Fatal error while reading config file: %s", err))
	}
	if viper.IsSet("auth.sessionSecret") {
		log.Printf("Setting the session secret to %s", viper.GetString("auth.sessionSecret"))
		os.Setenv("SESSION_SECRET", viper.GetString("auth.sessionSecret"))
	} else {
		os.Setenv("SESSION_SECRET", "123abc")
	}
}

func runMigrations() {
	storage.StorageEngine.GetDatastore().AutoMigrate(
		&models.Component{},
		&models.Account{},
		&models.User{},
		&models.Role{},
		&models.Page{},
		&models.Query{},
	)
	log.Println("Successfully run all migrations")
}
