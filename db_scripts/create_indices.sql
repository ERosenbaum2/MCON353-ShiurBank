CREATE INDEX idx_series_description ON shiur_series(description(100));
CREATE INDEX idx_recording_title ON shiur_recordings(title);
CREATE INDEX idx_recording_keywords ON shiur_recordings(keyword_1, keyword_2, keyword_3);
CREATE INDEX idx_rebbi_name ON rebbeim(fname, lname);
CREATE INDEX idx_topic_name ON topics(name);
CREATE INDEX idx_institution_name ON institutions(name);