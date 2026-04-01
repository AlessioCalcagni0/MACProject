Put your Firebase service account JSON in this folder with this exact name:

`firebase-service-account.json`

Expected final path on your machine:

`backend/secrets/firebase-service-account.json`

Expected path inside the Docker container:

`/app/secrets/firebase-service-account.json`

How to get it:

1. Open Firebase Console.
2. Select your project.
3. Go to `Project settings`.
4. Open the `Service accounts` tab.
5. Click `Generate new private key`.
6. Download the JSON file.
7. Rename it to `firebase-service-account.json`.
8. Place it in this folder.

Do not commit the real JSON file to git.

You can use `firebase-service-account.example.json` only as a shape reference.
