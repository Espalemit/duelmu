DUELMU - 80 VISUAL QUESTIONS - IMPLEMENTATION NOTES
Generated: 2026-11-09

This is a supplementary question pack, not a patch applied to the repo.
Existing repository (main) has 400 questions. These 80 IDs are 051..060
for each subject+grade, for a planned combined total of 480.

Contents
- questions_extra_80.json : 80 additional questions in the existing schema shape.
  Each question has one extra field: imageAsset = images/XYZ.png.
- images/*.png : 80 original question illustrations, 1250 x 600 pixels.

IMPORTANT: NOT DIRECTLY IMPORTABLE TO CURRENT APP WITHOUT CHANGES
- Current QuestionDto and domain Question do not define imageAsset;
  Json ignoreUnknownKeys means the images will be ignored by old code.
- Extend QuestionDto / Question with nullable imageAsset (String?).
- Render image in QuestionCard using painterResource or load asset via BitmapFactory.
  Images live in assets/images/; for res/drawable rename to lowercase e.g.
  fis_smp_051.png and build a drawable mapping.
- Merge the 80 questions with the existing app bank, set questionCount=480,
  update bankVersionName, regenerate BANK_SHA256, and update tests.
- Source kickstart/400 bank and tools/build_question_asset.py are hard-wired
  to 400; extend the build+validation pipeline before release.
- Both phones must have the EXACT SAME bank hash; host rejects mismatch.
- Review classroom correctness and diagrams with a teacher before production.

The PDFs contain all 80 questions, diagrams, answers and explanations.
