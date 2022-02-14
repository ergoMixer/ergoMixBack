# Ergo Mixer with stealth address feature
this is an improved version of ergo mixer with contain stealth address feature. you can find the main version [here](https://github.com/ergoMixer/ergoMixBack).

## about the project

The major project for stealth address is a customized version of ergo scanner, which you can find it [here](https://github.com/aragogi/scanner).
We take part in the ErgoHack3 and this is our result. Hope this can be part of the main mixer.

We discuss stealth address and its benefits in the [stealth scanner project](https://github.com/aragogi/scanner#stealth-address-scanner).
In this project, we embed our scanner into the mixer with some new features.

1. Scanner model and database re-designed and imported into the mixer.
2. We implement some APIs (talk about them later).
3. Some new services were added to the mixer to run the scanner and do the payments.

As you can see, we add these routes to the mixer:

| method  | route                                 | usage  |
| :---:   |  :---                                 | :---   |
| POST    | /stealth                              | by sending a name for stealth, we generate a new secret and a new stealth address for you |
| GET     | /stealth                              | you can get all of your stealth addresses with stealth name and the value you earned |
| GET     | /stealth/address/:pk                  | you can generate a stealth payment address by sending and stealth address to this route  |
| GET     | /stealth/:stealthId                   | you can get one specific stealth address and the value earned |
| GET     | /stealth/:stealthId/getUnspentBoxes   | get all of your unspent boxes for given stealthId  |
| POST    | /stealth/:stealthId/spend             | set spend address for each of your boxes to spend them  |

I write these routes here to tell you what is a new feature of the mixer. Also, there is a UI for them. We customize the mixer UI and add some pages so you can use the stealth address inside the mixer.

## Future of the project
- [ ] improve the scanner side and make it more simplified and efficient.
- [ ] Adding the restore feature to the stealth scanner, which the user can import the secret and find related boxes.
- [ ] Integration with wallets.
