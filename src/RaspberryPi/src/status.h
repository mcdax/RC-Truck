struct status {
	struct ultrasensor *ultrasensors[NUMBEROFULTRASENSORS];
	int mode;
	int speed;
	int newsockFD;
	bool connected;
	bool caution;
	bool lightsOn;
	bool lightsAutoOn;
	bool horn;
	bool hornPlaying;
	bool soundPlaying;
	bool ridin;
	bool ridinPlaying;
};

