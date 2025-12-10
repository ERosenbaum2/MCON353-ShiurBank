INSERT INTO rebbeim (title, fname, lname, user_id)
VALUES
    ('Rabbi', 'Person', 'Two', 9), -- make sure this user exists before running this
    ('Rabbi', 'Tzaddik', 'Choshuv', NULL),
    ('Rabbi', 'Holy', 'Mekubal', NULL);

INSERT INTO topics (name)
VALUES
    ('Chumash'),
    ('Daf Yomi'),
    ('Gemara'),
    ('Halacha'),
    ('Hashkafa'),
    ('Mishna'),
    ('Mussar'),
    ('Parsha'),
    ('Tanach');

INSERT INTO institutions (name) VALUES
                                    ('Yeshivas Yishrei Lev'),
                                    ('Beis Medrash L''Talmud'),
                                    ('Yeshivas Rav Yitzchok Elchanan'),
                                    ('Yeshiva Shaar Hatorah'),
                                    ('Yeshiva Darchei Torah');

INSERT INTO rebbi_institution_assoc (rebbi_id, inst_id)
VALUES -- make sure these values exist before running this
    (1, 3),
    (2, 2),
    (2, 5);

INSERT INTO subscriber_types (name)
VALUES
    ('On upload'),
    ('Daily'),
    ('Weekly');

