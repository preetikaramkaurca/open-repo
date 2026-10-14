/**
 * getCurrentDomain
 * @name getCurrentDomain
 * @param {scope} globals
 */
function getCurrentDomain() {
    return window.location.origin;
}


/**
 * Copies the current day's earliest and latest time onto every other visible day.
 * Call this from the repeatTimeRange rule when the checkbox value becomes "on".
 * Each day fragment must have "Group child components' data on form submission" enabled
 * so the shared field names stay independent.
 * @name updateOtherDay
 * @param {scope} globals
 */
function updateOtherDay( globals ) {
    if ( !globals || !globals.field || !globals.functions || globals.field.$value !== "on" ) {
        return;
    }

    var timeFields = [ "earliestTime", "earliestTimeAmPm", "latestTime", "latestTimeAmPm" ];
    var source = globals.field;
    var fragmentName = source.$fragment;
    var sourceFragment = source;

    while ( sourceFragment && sourceFragment.$qualifiedName !== fragmentName ) {
        sourceFragment = sourceFragment.$parent;
    }

    if ( !sourceFragment || !sourceFragment.$parent || !sourceFragment.$parent.$parent ) {
        return;
    }

    var values = {};
    timeFields.forEach( function ( name ) {
        var field = findFieldByName( sourceFragment, name );
        values[ name ] = field ? field.$value : null;
    } );

    var dayTabs = sourceFragment.$parent.$parent.$items || [];
    dayTabs.forEach( function ( day ) {
        if ( day.$visible === false ) {
            return;
        }

        ( day.$items || [] ).forEach( function ( child ) {
            if ( child.$qualifiedName === fragmentName || !findFieldByName( child, "earliestTime" ) ) {
                return;
            }

            timeFields.forEach( function ( name ) {
                var target = findFieldByName( child, name );
                if ( target ) {
                    globals.functions.setProperty( target, { value: values[ name ] } );
                }
            } );
        } );
    } );

    function findFieldByName( container, name ) {
        var items = container.$items || [];
        for ( var i = 0; i < items.length; i++ ) {
            var child = items[ i ];
            if ( child.$name === name ) {
                return child;
            }
            if ( child.$items && child.$items.length ) {
                var nested = findFieldByName( child, name );
                if ( nested ) {
                    return nested;
                }
            }
        }
        return null;
    }
}


/**
    * @name setEnums
    * @returns {string[]}
    **/
function setEnums() {

    var options = [];
    var minutes = 15;
    var end = ( 11 * 60 ) + 15;

    while ( minutes <= end ) {
        var hours = Math.floor( minutes / 60 );
        var mins = minutes % 60;
        var label = ( hours < 10 ? "0" : "" ) + hours + ":" + ( mins < 10 ? "0" : "" ) + mins;
        options.push( label );
        minutes += 15;
    }

    return options;
    
}

/**
* @name setEnumNames
* @returns {string[]}
**/
function setEnumNames() {

    var options = [];
    var minutes = 15;
    var end = ( 11 * 60 ) + 15;

    while ( minutes <= end ) {
        var hours = Math.floor( minutes / 60 );
        var mins = minutes % 60;
        var label = ( hours < 10 ? "0" : "" ) + hours + ":" + ( mins < 10 ? "0" : "" ) + mins;
        options.push( label );
        minutes += 15;
    }

    return options;
}


/**
    * signupFormSubmitData
    * @name signupFormSubmitData
    * @param {scope} globals
    */
function signupFormSubmitData( globals ) {
    var data = globals.functions.exportData();
    data.BacFormResponseDto.businessDetails.primaryRole = convertToArray( data.BacFormResponseDto.businessDetails.primaryRole );
    globals.functions.submitForm( data, false );
}

let autocomplete;
let googleStreetField;
let googlePostalCodeField;
let googleCityField;
let googleStateField;
let operateAsField;

function initAutocomplete() {
    googleStreetField = document.querySelector( ".google-street .cmp-adaptiveform-textinput__widget" );
    googlePostalCodeField = document.querySelector( ".google-zipcode .cmp-adaptiveform-textinput__widget" );
    googleCityField = document.querySelector( ".google-city .cmp-adaptiveform-textinput__widget" );
    googleStateField = document.querySelector( ".google-state .cmp-adaptiveform-dropdown__widget" );
    operateAsField = document.querySelector( ".dropdown-operate-as .cmp-adaptiveform-dropdown__widget" );

    if ( googleStreetField ) {
        autocomplete = new google.maps.places.Autocomplete( googleStreetField, {
            componentRestrictions: { country: [ "us" ] },
            fields: [ "address_components", "geometry" ],
            types: [ "address" ],
        } );
        autocomplete.addListener( "place_changed", fillInAddress );


    }
}

function fillInAddress() {
    const place = autocomplete.getPlace();
    let address = "";
    let postcode = "";
    let state = "";

    for ( const component of place.address_components ) {
        const componentType = component.types[ 0 ];

        switch ( componentType ) {
            case "street_number":
                address = `${ component.long_name } ${ address }`;
                break;
            case "route":
                address += component.short_name;
                break;
            case "postal_code":
                postcode = `${ component.long_name }${ postcode }`;
                break;
            case "postal_code_suffix":
                postcode = `${ postcode }-${ component.long_name }`;
                break;
            case "locality":
                if ( googleCityField ) {
                    googleCityField.value = component.long_name;
                    googleCityField.dispatchEvent( new Event( "blur", { bubbles: true } ) );
                    googleCityField.removeAttribute( 'disabled' );
                }

                break;
            case "administrative_area_level_1":
                state = component.short_name;
                break;
        }
    }

    googleStreetField.value = address;
   
    if( postcode ) {
        googlePostalCodeField.value = postcode;
        googlePostalCodeField.dispatchEvent( new Event( "blur", { bubbles: true } ) );
        googlePostalCodeField.removeAttribute( 'disabled' );
    }

    if ( state ) {
        googleStateField.value = state;
        if(operateAsField) {
            operateAsField.removeAttribute( "disabled" );
        }
        googleStateField.dispatchEvent( new Event( 'change' ) );
    }

}

document.addEventListener( "DOMContentLoaded", initAutocomplete );

/**
 * Quarter-hour times from 00:15 through 11:15.
 * Bind this output to both enum and enumNames of the Earliest Time dropdown.
 * @name earliestTimeOptions
 * @returns {string[]}
 */
function earliestTimeOptions() {
    var options = [];
    var minutes = 15;
    var end = ( 11 * 60 ) + 15;

    while ( minutes <= end ) {
        var hours = Math.floor( minutes / 60 );
        var mins = minutes % 60;
        var label = ( hours < 10 ? "0" : "" ) + hours + ":" + ( mins < 10 ? "0" : "" ) + mins;
        options.push( label );
        minutes += 15;
    }

    return options;
}
